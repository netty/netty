/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.memcache;

import io.netty.buffer.ByteBufHolder;
import io.netty.util.AbstractReferenceCounted;

/**
 * An abstract byte buf holder to solve the gap between the byte buf holder and the reference
 * counted implementation.
 */
public abstract class AbstractByteBufHolder extends AbstractReferenceCounted implements ByteBufHolder {

    @Override
    protected void deallocate() {

    }

    @Override
    public AbstractByteBufHolder retain() {
        super.retain();
        return this;
    }

    @Override
    public AbstractByteBufHolder retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public AbstractByteBufHolder touch() {
        super.touch();
        return this;
    }
}
