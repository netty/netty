/*
 * Copyright 2014 The Netty Project
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
package io.netty5.testsuite.transport;

import io.netty5.bootstrap.AbstractBootstrap;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.PooledByteBufAllocator;
import io.netty5.buffer.UnpooledByteBufAllocator;
import io.netty5.buffer.api.BufferAllocator;

import java.util.List;

public final class TestsuitePermutation {

    public static List<AllocatorConfig> allocator() {
        return List.of(
                new AllocatorConfig(UnpooledByteBufAllocator.DEFAULT, BufferAllocator.offHeapUnpooled()),
                new AllocatorConfig(PooledByteBufAllocator.DEFAULT, BufferAllocator.offHeapPooled()));
    }

    private TestsuitePermutation() { }

    public interface BootstrapFactory<CB extends AbstractBootstrap<?, ?, ?>> {
        CB newInstance();
    }

    public interface BootstrapComboFactory<SB extends AbstractBootstrap<?, ?, ?>,
            CB extends AbstractBootstrap<?, ?, ?>> {
        SB newServerInstance();
        CB newClientInstance();
    }

    public static final class AllocatorConfig {
        public final ByteBufAllocator byteBufAllocator;
        public final BufferAllocator bufferAllocator;

        public AllocatorConfig(ByteBufAllocator byteBufAllocator, BufferAllocator bufferAllocator) {
            this.byteBufAllocator = byteBufAllocator;
            this.bufferAllocator = bufferAllocator;
        }
    }
}
