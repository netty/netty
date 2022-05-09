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
import io.netty5.buffer.api.BufferAllocator;

import java.util.List;

public final class TestsuitePermutation {
    private static final List<AllocatorConfig> ALLOCATOR_CONFIGS = List.of(
            new AllocatorConfig(BufferAllocator.offHeapUnpooled()),
            new AllocatorConfig(BufferAllocator.offHeapPooled()));

    public static List<AllocatorConfig> allocator() {
        return ALLOCATOR_CONFIGS;
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
        public final BufferAllocator bufferAllocator;

        public AllocatorConfig(BufferAllocator bufferAllocator) {
            this.bufferAllocator = bufferAllocator;
        }
    }
}
