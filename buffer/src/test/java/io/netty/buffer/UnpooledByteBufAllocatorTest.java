/*
 * Copyright 2017 The Netty Project
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
package io.netty.buffer;

import org.junit.jupiter.api.Disabled;

public class UnpooledByteBufAllocatorTest extends AbstractByteBufAllocatorTest<UnpooledByteBufAllocator> {

    @Override
    protected UnpooledByteBufAllocator newAllocator(boolean preferDirect) {
        return new UnpooledByteBufAllocator(preferDirect);
    }

    @Override
    protected UnpooledByteBufAllocator newUnpooledAllocator() {
        return new UnpooledByteBufAllocator(false);
    }

    @Disabled("Not applicable for unpooled allocators")
    @Override
    public void shouldReuseChunks() throws Exception {
    }
}
