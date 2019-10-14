/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.asJava.classes

import com.intellij.psi.*
import com.intellij.psi.impl.PsiImplUtil
import com.intellij.psi.impl.PsiSuperMethodImplUtil
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.impl.light.LightTypeParameterListBuilder
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod
import org.jetbrains.kotlin.asJava.builder.LightMemberOrigin
import org.jetbrains.kotlin.asJava.builder.LightMemberOriginForDeclaration
import org.jetbrains.kotlin.asJava.builder.MemberIndex
import org.jetbrains.kotlin.asJava.elements.KtLightAbstractAnnotation
import org.jetbrains.kotlin.asJava.elements.KtLightMethodImpl
import org.jetbrains.kotlin.codegen.FunctionCodegen
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.load.java.BuiltinMethodsWithSpecialGenericSignature.getSpecialSignatureInfo
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOriginKind

internal abstract class KtUltraLightMethod(
    internal val delegate: PsiMethod,
    lightMemberOrigin: LightMemberOrigin?,
    protected val support: KtUltraLightSupport,
    containingClass: KtLightClass
) : KtLightMethodImpl(
    { delegate },
    lightMemberOrigin,
    containingClass
), KtUltraLightElementWithNullabilityAnnotation<KtDeclaration, PsiMethod> {

    private class KtUltraLightThrowsReferenceListBuilder(private val parentMethod: PsiMethod) :
        KotlinLightReferenceListBuilder(parentMethod.manager, parentMethod.language, PsiReferenceList.Role.THROWS_LIST) {
        override fun getParent() = parentMethod
        override fun getContainingFile() = parentMethod.containingFile
    }

    protected fun computeThrowsList(methodDescriptor: FunctionDescriptor?): PsiReferenceList {
        val builder = KtUltraLightThrowsReferenceListBuilder(parentMethod = this)

        if (methodDescriptor !== null) {
            for (ex in FunctionCodegen.getThrownExceptions(methodDescriptor)) {
                val psiClassType = ex.defaultType.asPsiType(support, TypeMappingMode.DEFAULT, builder) as? PsiClassType
                psiClassType ?: continue
                builder.addReference(psiClassType)
            }
        }

        return builder
    }

    protected fun computeCheckNeedToErasureParametersTypes(methodDescriptor: FunctionDescriptor?): Boolean {
        return methodDescriptor
            ?.getSpecialSignatureInfo()
            ?.let { it.valueParametersSignature !== null }
            ?: false
    }

    abstract override fun buildTypeParameterList(): PsiTypeParameterList

    abstract val checkNeedToErasureParametersTypes: Boolean

    override val memberIndex: MemberIndex? = null

    override val psiTypeForNullabilityAnnotation: PsiType?
        get() = returnType

    // These two overrides are necessary because ones from KtLightMethodImpl suppose that clsDelegate.returnTypeElement is valid
    // While here we only set return type for LightMethodBuilder (see org.jetbrains.kotlin.asJava.classes.KtUltraLightClass.asJavaMethod)
    override fun getReturnTypeElement(): PsiTypeElement? = null

    override fun getReturnType(): PsiType? = clsDelegate.returnType

    override fun buildParametersForList(): List<PsiParameter> = clsDelegate.parameterList.parameters.toList()

    // should be in super
    override fun isVarArgs() = PsiImplUtil.isVarArgs(this)

    private val _deprecated: Boolean by lazyPub { kotlinOrigin?.isDeprecated(support) ?: false }

    override fun getHierarchicalMethodSignature() = PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this)

    override fun findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean): List<MethodSignatureBackedByPsiMethod> =
        PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess)

    override fun findDeepestSuperMethod() = PsiSuperMethodImplUtil.findDeepestSuperMethod(this)

    override fun findDeepestSuperMethods(): Array<out PsiMethod> = PsiSuperMethodImplUtil.findDeepestSuperMethods(this)

    override fun findSuperMethods(): Array<out PsiMethod> = PsiSuperMethodImplUtil.findSuperMethods(this)

    override fun findSuperMethods(checkAccess: Boolean): Array<out PsiMethod> =
        PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess)

    override fun findSuperMethods(parentClass: PsiClass?): Array<out PsiMethod> =
        PsiSuperMethodImplUtil.findSuperMethods(this, parentClass)

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = name.hashCode()

    override fun isDeprecated(): Boolean = _deprecated
}

internal class KtUltraLightMethodForSourceDeclaration(
    delegate: PsiMethod,
    lightMemberOrigin: LightMemberOrigin?,
    support: KtUltraLightSupport,
    containingClass: KtLightClass,
    private val forceToSkipNullabilityAnnotation: Boolean = false
) : KtUltraLightMethod(
    delegate,
    lightMemberOrigin,
    support,
    containingClass
) {
    constructor(
        delegate: PsiMethod,
        declaration: KtDeclaration,
        support: KtUltraLightSupport,
        containingClass: KtLightClass
    ) : this(delegate, LightMemberOriginForDeclaration(declaration, JvmDeclarationOriginKind.OTHER), support, containingClass)

    override val qualifiedNameForNullabilityAnnotation: String?
        get() {
            val typeForAnnotation = if (forceToSkipNullabilityAnnotation) null else kotlinOrigin?.getKotlinType()
            return computeQualifiedNameForNullabilityAnnotation(typeForAnnotation)
        }

    override fun buildTypeParameterList(): PsiTypeParameterList {
        val origin = kotlinOrigin
        return if (origin is KtFunction || origin is KtProperty)
            buildTypeParameterList(origin as KtTypeParameterListOwner, this, support)
        else LightTypeParameterListBuilder(manager, language)
    }

    private val methodDescriptor get() = kotlinOrigin?.resolve() as? FunctionDescriptor

    private val _throwsList: PsiReferenceList by lazyPub { computeThrowsList(methodDescriptor) }
    override fun getThrowsList(): PsiReferenceList = _throwsList

    override val checkNeedToErasureParametersTypes: Boolean by lazyPub { computeCheckNeedToErasureParametersTypes(methodDescriptor) }
}

internal class KtUltraLightMethodForDescriptor(
    methodDescriptor: FunctionDescriptor,
    delegate: LightMethodBuilder,
    lightMemberOrigin: LightMemberOrigin?,
    support: KtUltraLightSupport,
    containingClass: KtUltraLightClass
) : KtUltraLightMethod(
    delegate,
    lightMemberOrigin,
    support,
    containingClass
) {
    private val _buildTypeParameterList = buildTypeParameterList(methodDescriptor, this, support)
    override fun buildTypeParameterList() = _buildTypeParameterList

    private val _throwsList: PsiReferenceList = computeThrowsList(methodDescriptor)
    override fun getThrowsList(): PsiReferenceList = _throwsList

    override val qualifiedNameForNullabilityAnnotation: String? =
        computeQualifiedNameForNullabilityAnnotation(methodDescriptor.returnType)

    override val givenAnnotations: List<KtLightAbstractAnnotation> = methodDescriptor.obtainLightAnnotations(support, this)

    override val checkNeedToErasureParametersTypes: Boolean = computeCheckNeedToErasureParametersTypes(methodDescriptor)
}