/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.java.types

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.java.JavaTypeParameterStack
import org.jetbrains.kotlin.fir.java.toConeKotlinTypeWithNullability
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.fir.visitors.transformInplace
import org.jetbrains.kotlin.load.java.structure.JavaType

class FirJavaTypeRef internal constructor(
    annotations: List<FirAnnotationCall>,
    val javaType: JavaType,
    session: FirSession,
    javaTypeParameterStack: JavaTypeParameterStack
) : FirResolvedTypeRef {
    override val psi: PsiElement?
        get() = null

    override val type: ConeKotlinType = javaType.toConeKotlinTypeWithNullability(
        session, javaTypeParameterStack, mapToKotlin = true, isNullable = false
    )

    override val annotations: MutableList<FirAnnotationCall> = annotations.toMutableList()

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {
        annotations.forEach { it.accept(visitor, data) }
    }

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirJavaTypeRef {
        annotations.transformInplace(transformer, data)
        return this
    }
}