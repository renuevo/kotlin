/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.fir.visitors.*

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

interface FirTypeProjectionWithVariance : FirTypeProjection {
    override val psi: PsiElement?
    val typeRef: FirTypeRef
    val variance: Variance

    override fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R = visitor.visitTypeProjectionWithVariance(this, data)
}
