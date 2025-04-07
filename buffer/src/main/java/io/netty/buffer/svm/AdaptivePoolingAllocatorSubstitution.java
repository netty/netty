/*
 * Copyright 2025 The Netty Project
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
package io.netty.buffer.svm;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "io.netty.buffer.AdaptivePoolingAllocator$Chunk")
final class AdaptivePoolingAllocatorSubstitution {
    private AdaptivePoolingAllocatorSubstitution() {
    }

    @Alias
    @RecomputeFieldValue(
            kind = RecomputeFieldValue.Kind.FieldOffset,
            declClassName = "io.netty.buffer.AdaptivePoolingAllocator$Chunk",
            name = "refCnt"
    )
    public static long REFCNT_FIELD_OFFSET;

}
