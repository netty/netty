/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer;

import java.nio.ByteOrder;

public abstract class AbstractNonWrappedByteBuf extends AbstractByteBuf {

    private Unsafe unsafe;

    protected AbstractNonWrappedByteBuf(ByteOrder endianness, int maxCapacity) {
        super(endianness, maxCapacity);
    }

    @Override
    public final Unsafe unsafe() {
        Unsafe unsafe = this.unsafe;
        if (unsafe == null) {
            this.unsafe = unsafe = newUnsafe();
        }
        return unsafe;
    }

    protected abstract Unsafe newUnsafe();
}
