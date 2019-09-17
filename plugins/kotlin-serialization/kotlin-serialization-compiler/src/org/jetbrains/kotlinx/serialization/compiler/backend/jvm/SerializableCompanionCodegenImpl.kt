/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlinx.serialization.compiler.backend.jvm

import org.jetbrains.kotlin.codegen.ImplementationBodyCodegen
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.jvm.diagnostics.OtherOrigin
import org.jetbrains.kotlinx.serialization.compiler.backend.common.SerializableCompanionCodegen
import org.jetbrains.kotlinx.serialization.compiler.backend.common.findTypeSerializer
import org.jetbrains.kotlinx.serialization.compiler.resolve.getSerializableClassDescriptorByCompanion
import org.jetbrains.kotlinx.serialization.compiler.resolve.shouldHaveGeneratedMethodsInCompanion
import org.jetbrains.kotlinx.serialization.compiler.resolve.toSimpleType
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.Opcodes.*
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

class SerializableCompanionCodegenImpl(private val classCodegen: ImplementationBodyCodegen) :
    SerializableCompanionCodegen(classCodegen.descriptor, classCodegen.bindingContext) {

    protected val thisAsmType = classCodegen.typeMapper.mapClass(classCodegen.descriptor)

    companion object {
        fun generateSerializableExtensions(codegen: ImplementationBodyCodegen) {
            val serializableClass = getSerializableClassDescriptorByCompanion(codegen.descriptor) ?: return
            if (serializableClass.shouldHaveGeneratedMethodsInCompanion)
                SerializableCompanionCodegenImpl(codegen).generate()
        }
    }

    override fun generateSerializerGetter(methodDescriptor: FunctionDescriptor) {
        val serial = requireNotNull(
            findTypeSerializer(
                serializableDescriptor.module,
                serializableDescriptor.toSimpleType()
            )
        )

        // helper function to reduce number of copy-pasted args
        fun instantiateSerializer(generator: InstructionAdapter?) {
            stackValueSerializerInstance(
                classCodegen,
                serializableDescriptor.module,
                serializableDescriptor.defaultType,
                serial,
                iv = generator,
                genericIndex = null
            ) { it, _ ->
                load(it + 1, kSerializerType)
            }
        }

        var canBeCached = true
        // serializer could not be cached if it depends on serializers for class generic parameters which are passed as .serializer() function arguments
        // first, we check this dependency (iv = null prevents actual instantiation).
        // caching serializers which are objects is meaningless
        if (serial.kind == ClassKind.OBJECT)
            canBeCached = false
        else {
            checkValueSerializerInstance(
                classCodegen,
                serializableDescriptor.module,
                serializableDescriptor.defaultType,
                serial,
                iv = null,
                genericIndex = null,
                genericSerializerFieldGetter = { _, _ -> canBeCached = false }
            )
        }
        // make a field to cache serializer
        if (canBeCached) classCodegen.v.newField(
            OtherOrigin(classCodegen.myClass.psiOrParent), ACC_PRIVATE or ACC_SYNTHETIC or ACC_VOLATILE,
            cacheFieldName, kSerializerType.descriptor, null, null
        )
        // generate actual getter
        classCodegen.generateMethod(methodDescriptor) { _, _ ->
            if (!canBeCached) {
                // instantiate and return
                instantiateSerializer(generator = this)
                areturn(kSerializerType)
                return@generateMethod
            }
            val createLabel = Label()
            // check cache first
            load(0, thisAsmType)
            getfield(thisAsmType.internalName, cacheFieldName, kSerializerType.descriptor)
            dup()
            ifnull(createLabel)
            areturn(kSerializerType)
            // if null, create & write
            visitLabel(createLabel)
            instantiateSerializer(generator = this)
            dup()
            load(0, thisAsmType)
            swap()
            putfield(thisAsmType.internalName, cacheFieldName, kSerializerType.descriptor)
            areturn(kSerializerType)
        }
    }
}