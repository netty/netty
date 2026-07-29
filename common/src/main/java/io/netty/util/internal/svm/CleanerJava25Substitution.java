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

/**
 * GraalVM substitution for {@link io.netty.util.internal.CleanerJava25}.
 * <p>
 * {@link io.netty.util.internal.CleanerJava25} is initialized at build time (see
 * {@code META-INF/native-image/io.netty/netty-common/native-image.properties}),
 * which means its static initializer — including the {@code Arena.ofShared()}
 * probe — runs against the HotSpot JDK 25 during the native image build.
 * On HotSpot the probe succeeds and {@code INVOKE_ALLOCATOR} is set to a
 * non-null {@link MethodHandle}, frozen into the native image heap.
 * <p>
 * At runtime on the GraalVM substrate (without {@code -H:+SharedArenaSupport}),
 * the static initializer never runs again. {@code INVOKE_ALLOCATOR} is already
 * non-null, {@code isSupported()} returns {@code true}, and the
 * {@code Arena.close()} code path is entered — hitting GraalVM's
 * {@code closeScope0Unsupported} stub and crashing with
 * {@link com.oracle.svm.core.jdk.UnsupportedFeatureError}.
 * <p>
 * This substitution resets {@code INVOKE_ALLOCATOR} to {@code null} in the
 * native image heap, so that {@code isSupported()} returns {@code false} and
 * the Arena code path is dead-code-eliminated.
 */
@TargetClass(className = "io.netty.util.internal.CleanerJava25")
final class CleanerJava25Substitution {

    private CleanerJava25Substitution() {
    }

    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)
    private static MethodHandle INVOKE_ALLOCATOR;
}