/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.atMostOne
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.ir.JvmIrBuilder
import org.jetbrains.kotlin.backend.jvm.ir.createJvmIrBuilder
import org.jetbrains.kotlin.backend.jvm.lower.inlineclasses.unboxInlineClass
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name

internal val jvmStringConcatenationLowering = makeIrFilePhase(
    ::JvmStringConcatenationLowering,
    name = "StringConcatenation",
    description = "Replace IrStringConcatenation with string builders"
)

/**
 * This lowering pass replaces [IrStringConcatenation]s with StringBuilder appends.
 */
private class JvmStringConcatenationLowering(val context: JvmBackendContext) : FileLoweringPass, IrElementTransformerVoidWithContext() {
    override fun lower(irFile: IrFile) = irFile.transformChildrenVoid()

    private val typesWithSpecialAppendFunction = context.irBuiltIns.primitiveIrTypes + context.irBuiltIns.stringType

    private val nameToString = Name.identifier("toString")
    private val nameAppend = Name.identifier("append")

    private val stringBuilder = context.ir.symbols.stringBuilder.owner

    private val constructor = stringBuilder.constructors.single {
        it.valueParameters.size == 0
    }

    private val toStringFunction = stringBuilder.functions.single {
        it.valueParameters.size == 0 && it.name == nameToString
    }

    private val defaultAppendFunction = stringBuilder.functions.single {
        it.name == nameAppend &&
                it.valueParameters.size == 1 &&
                it.valueParameters.single().type.isNullableAny()
    }

    private val appendFunctions: Map<IrType, IrSimpleFunction?> =
        typesWithSpecialAppendFunction.map { type ->
            type to stringBuilder.functions.toList().atMostOne {
                it.name == nameAppend && it.valueParameters.singleOrNull()?.type == type
            }
        }.toMap()

    private fun typeToAppendFunction(type: IrType): IrSimpleFunction {
        return appendFunctions[type] ?: defaultAppendFunction
    }

    private fun JvmIrBuilder.callToString(expression: IrExpression): IrExpression {
        if (expression.type.isString())
            return expression

        // There is no String.valueOf for bytes or shorts.
        val argument = if (expression.type.isByte() || expression.type.isShort())
            irImplicitCast(expression, context.irBuiltIns.intType)
        else expression

        val argumentType = if (argument.type.isPrimitiveType()) argument.type else context.irBuiltIns.anyNType

        val function = backendContext.ir.symbols.javaLangString.functions.single {
            it.owner.name.asString() == "valueOf" && it.owner.valueParameters.singleOrNull()?.type == argumentType
        }

        return irCall(function).apply {
            putValueArgument(0, argument)
        }
    }

    private fun JvmIrBuilder.lowerInlineClassArgument(expression: IrExpression): IrExpression {
        if (expression.type.unboxInlineClass() == expression.type)
            return expression

        val toStringFunction = expression.type.classOrNull?.owner?.functions?.singleOrNull {
            it.name.asString() == "toString" && it.dispatchReceiverParameter != null
                    && it.extensionReceiverParameter == null
                    && it.valueParameters.isEmpty()
                    && it.overriddenSymbols.isNotEmpty()
        } ?: return expression

        val toStringReplacement = backendContext.inlineClassReplacements.getReplacementFunction(toStringFunction)
            ?: return expression

        return irCall(toStringReplacement.function).apply {
            putValueArgument(0, expression)
        }
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation): IrExpression {
        expression.transformChildrenVoid(this)
        return context.createJvmIrBuilder(currentScope!!.scope.scopeOwnerSymbol, expression.startOffset, expression.endOffset).run {
            val arguments = expression.arguments.map { lowerInlineClassArgument(it) }

            when {
                arguments.isEmpty() ->
                    irString("")

                arguments.size == 1 ->
                    callToString(arguments.single())

                arguments.size == 2 && arguments[0].type.isStringClassType() ->
                    irCall(backendContext.ir.symbols.intrinsicStringPlus).apply {
                        putValueArgument(0, arguments[0])
                        putValueArgument(1, arguments[1])
                    }

                else -> {
                    var stringBuilder = irCall(constructor)
                    for (arg in arguments) {
                        val appendFunction = typeToAppendFunction(arg.type)
                        stringBuilder = irCall(appendFunction).apply {
                            dispatchReceiver = stringBuilder
                            putValueArgument(0, arg)
                        }
                    }
                    irCall(toStringFunction).apply {
                        dispatchReceiver = stringBuilder
                    }
                }
            }
        }
    }
}
