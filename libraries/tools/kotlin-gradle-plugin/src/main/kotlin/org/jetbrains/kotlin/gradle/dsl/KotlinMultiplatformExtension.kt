/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.dsl

import com.android.build.gradle.*
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkLocationSourceSet
import com.android.build.gradle.internal.SdkLocator
import com.android.builder.model.AndroidProject
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper
import com.android.tools.lint.gradle.api.ToolingRegistryProvider
import groovy.lang.Closure
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.util.ConfigureUtil
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetPreset
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetsContainerWithPresets
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.utils.isGradleVersionAtLeast
import java.io.File

open class KotlinMultiplatformExtension : KotlinProjectExtension(), KotlinTargetContainerWithPresetFunctions {
    override lateinit var presets: NamedDomainObjectCollection<KotlinTargetPreset<*>>
        internal set

    override lateinit var targets: NamedDomainObjectCollection<KotlinTarget>
        internal set

    internal var isGradleMetadataAvailable: Boolean = false
    internal var isGradleMetadataExperimental: Boolean = false

    fun metadata(configure: KotlinOnlyTarget<KotlinCommonCompilation>.() -> Unit = { }): KotlinOnlyTarget<KotlinCommonCompilation> =
        @Suppress("UNCHECKED_CAST")
        (targets.getByName(KotlinMultiplatformPlugin.METADATA_TARGET_NAME) as KotlinOnlyTarget<KotlinCommonCompilation>).also(configure)

    fun metadata(configure: Closure<*>) = metadata { ConfigureUtil.configure(configure, this) }

    fun <T : KotlinTarget> targetFromPreset(
        preset: KotlinTargetPreset<T>,
        name: String = preset.name,
        configure: T.() -> Unit = { }
    ): T = configureOrCreate(name, preset, configure)

    fun targetFromPreset(preset: KotlinTargetPreset<*>, name: String, configure: Closure<*>) =
        targetFromPreset(preset, name) { ConfigureUtil.configure(configure, this) }

    fun targetFromPreset(preset: KotlinTargetPreset<*>) = targetFromPreset(preset, preset.name) { }
    fun targetFromPreset(preset: KotlinTargetPreset<*>, name: String) = targetFromPreset(preset, name) { }
    fun targetFromPreset(preset: KotlinTargetPreset<*>, configure: Closure<*>) = targetFromPreset(preset, preset.name, configure)

    internal val rootSoftwareComponent: KotlinSoftwareComponent by lazy {
        if (isGradleVersionAtLeast(4, 7)) {
            KotlinSoftwareComponentWithCoordinatesAndPublication("kotlin", targets)
        } else {
            KotlinSoftwareComponent("kotlin", targets)
        }
    }

    fun forEachVariant(project: Project, action: (BaseVariant) -> Unit) {
        val logger = LoggerWrapper(project.logger)
        val androidExtension = project.extensions.findByName("android") as BaseExtension? ?: return
//        val sdkInfo = SdkHandler(project, logger).sdkInfo
        when (androidExtension) {
            is AppExtension -> androidExtension.applicationVariants.all(action)
            is LibraryExtension -> {
                androidExtension.libraryVariants.all(action)
                if (androidExtension is FeatureExtension) {
                    androidExtension.featureVariants.all(action)
                }
            }
            is TestExtension -> androidExtension.applicationVariants.all(action)
        }
        if (androidExtension is TestedExtension) {
            androidExtension.testVariants.all(action)
            androidExtension.unitTestVariants.all(action)
        }
    }

    fun getAndroidSdkJar(project: Project): String? {
        val androidExtension = project.extensions.findByName("android") as BaseExtension? ?: return null
        val sdkLocation = SdkLocator.getSdkLocation(SdkLocationSourceSet(project.rootDir)).directory ?: return null
        val sdkHandler = AndroidSdkHandler.getInstance(sdkLocation)
        val logger = LoggerProgressIndicatorWrapper(LoggerWrapper(project.logger))
        val androidTarget = sdkHandler.getAndroidTargetManager(logger).getTargetFromHashString(androidExtension.compileSdkVersion, logger)
        return androidTarget.getPath(IAndroidTarget.ANDROID_JAR)
    }

    fun getAndroidConfigurations(project: Project): List<Configuration> {
        val configurations = ArrayList<Configuration>()
        forEachVariant(project) {
            configurations.add(it.compileConfiguration)
        }
        val ext = project.extensions.getByName("android") as BaseExtension

        val ssets = ext.sourceSets

        return configurations
    }

    private fun createAndroidProject(
        gradleProject: org.gradle.api.Project
    ): AndroidProject? {
        val pluginContainer = gradleProject.plugins
        for (p in pluginContainer) {
            if (p is ToolingRegistryProvider) {
                val registry: ToolingModelBuilderRegistry = (p as ToolingRegistryProvider).modelBuilderRegistry
                val modelName = AndroidProject::class.java.name
                val builder = registry.getBuilder(modelName)
                assert(builder.canBuild(modelName)) { modelName }

                val ext = gradleProject.extensions.extraProperties

                ext.set(
                    AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED,
                    AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD.toString()
                )

                try {
                    return builder.buildAll(modelName, gradleProject) as AndroidProject
                } finally {
                    ext.set(AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED, null)
                }
            }
        }

        return null
    }

    fun getDepGraph(project: org.gradle.api.Project): List<File>? {
        val androidProject = createAndroidProject(project) ?: return null
        val mainArtifact = androidProject.variants.iterator().next().mainArtifact
        val dependencies = mainArtifact.dependencies
        return dependencies.libraries.map { it.jarFile } + dependencies.javaLibraries.map { it.jarFile }
    }

}

internal fun KotlinTarget.isProducedFromPreset(kotlinTargetPreset: KotlinTargetPreset<*>): Boolean =
    preset == kotlinTargetPreset

internal fun <T : KotlinTarget> KotlinTargetsContainerWithPresets.configureOrCreate(
    targetName: String,
    targetPreset: KotlinTargetPreset<T>,
    configure: T.() -> Unit
): T {
    val existingTarget = targets.findByName(targetName)
    when {
        existingTarget?.isProducedFromPreset(targetPreset) ?: false -> {
            @Suppress("UNCHECKED_CAST")
            configure(existingTarget as T)
            return existingTarget
        }
        existingTarget == null -> {
            val newTarget = targetPreset.createTarget(targetName)
            targets.add(newTarget)
            configure(newTarget)
            return newTarget
        }
        else -> {
            throw InvalidUserCodeException(
                "The target '$targetName' already exists, but it was not created with the '${targetPreset.name}' preset. " +
                        "To configure it, access it by name in `kotlin.targets`" +
                        " or use the preset function '${existingTarget.preset?.name}'."
                            .takeIf { existingTarget.preset != null } ?: "."
            )
        }
    }
}
