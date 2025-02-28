/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.expressions

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.references.FirReference
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.visitors.*

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

interface FirDelegatedConstructorCall : FirCall {
    override val psi: PsiElement?
    override val annotations: List<FirAnnotationCall>
    override val arguments: List<FirExpression>
    val constructedTypeRef: FirTypeRef
    val isThis: Boolean
    val isSuper: Boolean
    val calleeReference: FirReference

    override fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R = visitor.visitDelegatedConstructorCall(this, data)

    override fun <D> transformArguments(transformer: FirTransformer<D>, data: D): FirDelegatedConstructorCall

    fun <D> transformCalleeReference(transformer: FirTransformer<D>, data: D): FirDelegatedConstructorCall
}
