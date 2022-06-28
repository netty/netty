/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api.internal;

import io.netty5.buffer.api.AllocatorControl;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Drop;

public abstract class AdaptableBuffer<T extends ResourceSupport<Buffer, T>>
        extends ResourceSupport<Buffer, T> implements Buffer {

    protected final AllocatorControl control;

    protected AdaptableBuffer(Drop<T> drop, AllocatorControl control) {
        super(drop);
        this.control = control;
    }

    @Override
    public AdaptableBuffer<T> touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Buffer && Statics.equals(this, (Buffer) o);
    }

    @Override
    public int hashCode() {
        return Statics.hashCode(this);
    }
}
