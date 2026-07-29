/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal.svm;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

import java.lang.invoke.MethodHandle;

@TargetClass(className = "io.netty.util.internal.CleanerJava25")
final class CleanerJava25Substitution {

    private CleanerJava25Substitution() {
    }

    /**
     * Resetting the INVOKE_ALLOCATOR field to null ensures that
     * {@link io.netty.util.internal.CleanerJava25#isSupported()} returns false
     * at runtime on GraalVM native images that do not have -H:+SharedArenaSupport
     * enabled.
     *
     * The static initializer in CleanerJava25 runs at build time because of the
     * {@code --initialize-at-build-time=io.netty.util.internal.CleanerJava25}
     * declaration in native-image.properties. On HotSpot JDK 25, the
     * Arena.ofShared().close() probe in the static initializer succeeds, so
     * INVOKE_ALLOCATOR is set to a non-null MethodHandle. This value is frozen
     * into the native image heap. At runtime on the GraalVM substrate, the
     * static initializer never runs again, and INVOKE_ALLOCATOR is already
     * non-null, causing isSupported() to return true even though Arena.close()
     * will crash with an UnsupportedFeatureError.
     *
     * Recomputing the field to null at image-build time restores the correct
     * runtime behavior: isSupported() returns false, and the Arena code path
     * is dead-code-eliminated.
     */
    @Alias
    @RecomputeFieldValue(
        kind = RecomputeFieldValue.Kind.Reset)
    private static MethodHandle INVOKE_ALLOCATOR;
}
