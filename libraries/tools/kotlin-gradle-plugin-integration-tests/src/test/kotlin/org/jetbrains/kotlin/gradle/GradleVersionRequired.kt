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

package org.jetbrains.kotlin.gradle

import org.gradle.util.GradleVersion
import org.junit.Assume

sealed class GradleVersionRequired(val minVersion: String, val maxVersion: String) {
    companion object {
        private val OLDEST_SUPPORTED = "4.9"
        private val NEWEST_TESTED = "5.3-rc-2"
    }

    init {
        check(minVersion.toGradleVersion() >= OLDEST_SUPPORTED.toGradleVersion()) {
            "minVersion ($minVersion) is older than the oldest supported ($OLDEST_SUPPORTED)"
        }
        check(maxVersion.toGradleVersion() <= NEWEST_TESTED.toGradleVersion()) {
            "maxVersion ($maxVersion) is newer than the newest tested ($NEWEST_TESTED)"
        }
    }

    class Exact(version: String) : GradleVersionRequired(version, version)

    class AtLeast(version: String) : GradleVersionRequired(version, NEWEST_TESTED)

    class InRange(minVersion: String, maxVersion: String) : GradleVersionRequired(minVersion, maxVersion)

    class Until(maxVersion: String) : GradleVersionRequired(OLDEST_SUPPORTED, maxVersion)

    object None : GradleVersionRequired(OLDEST_SUPPORTED, NEWEST_TESTED)
}


fun BaseGradleIT.Project.chooseWrapperVersionOrFinishTest(): String = gradleVersionRequirement.run {
    if (testCase.useMinVersion) return minVersion

    // we don't want to test with the same version twice
    Assume.assumeTrue(minVersion != maxVersion)
    return maxVersion
}

private fun String.toGradleVersion() = GradleVersion.version(this)