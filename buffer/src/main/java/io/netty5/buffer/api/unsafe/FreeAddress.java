/*
 * Copyright 2022 The Netty Project
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
package io.netty5.buffer.api.unsafe;

import io.netty5.buffer.api.internal.Statics;
import io.netty5.util.internal.PlatformDependent;

class FreeAddress implements Runnable {
    private final long address;
    private final int size;

    FreeAddress(long address, int size) {
        this.address = address;
        this.size = size;
    }

    @Override
    public void run() {
        PlatformDependent.freeMemory(address);
        Statics.MEM_USAGE_NATIVE.add(-size);
    }
}
