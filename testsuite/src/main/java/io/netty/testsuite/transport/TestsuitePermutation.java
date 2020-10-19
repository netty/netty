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
package io.netty.testsuite.transport;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;

import java.util.ArrayList;
import java.util.List;

public final class TestsuitePermutation {

    public static List<ByteBufAllocator> allocator() {
        List<ByteBufAllocator> allocators = new ArrayList<ByteBufAllocator>();
        allocators.add(UnpooledByteBufAllocator.DEFAULT);
        allocators.add(PooledByteBufAllocator.DEFAULT);
        return allocators;
    }

    private TestsuitePermutation() { }

    public interface BootstrapFactory<CB extends AbstractBootstrap<?, ?>> {
        CB newInstance();
    }

    public interface BootstrapComboFactory<SB extends AbstractBootstrap<?, ?>, CB extends AbstractBootstrap<?, ?>> {
        SB newServerInstance();
        CB newClientInstance();
    }
}
