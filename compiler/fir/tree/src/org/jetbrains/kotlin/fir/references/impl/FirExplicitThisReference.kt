/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.references.impl

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.references.FirThisReference
import org.jetbrains.kotlin.fir.symbols.AbstractFirBasedSymbol
import org.jetbrains.kotlin.fir.visitors.*

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

class FirExplicitThisReference(
    override val psi: PsiElement?,
    override val labelName: String?
) : FirThisReference {
    override var boundSymbol: AbstractFirBasedSymbol<*>? = null

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {}

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirExplicitThisReference {
        return this
    }

    override fun replaceBoundSymbol(newBoundSymbol: AbstractFirBasedSymbol<*>) {
        boundSymbol = newBoundSymbol
    }
}
