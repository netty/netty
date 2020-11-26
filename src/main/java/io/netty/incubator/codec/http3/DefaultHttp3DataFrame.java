/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

public final class DefaultHttp3DataFrame extends DefaultByteBufHolder implements Http3DataFrame {

    public DefaultHttp3DataFrame(ByteBuf data) {
        super(data);
    }

    @Override
    public Http3DataFrame copy() {
        return new DefaultHttp3DataFrame(content().copy());
    }

    @Override
    public Http3DataFrame duplicate() {
        return new DefaultHttp3DataFrame(content().duplicate());
    }

    @Override
    public Http3DataFrame retainedDuplicate() {
        return new DefaultHttp3DataFrame(content().retainedDuplicate());
    }

    @Override
    public Http3DataFrame replace(ByteBuf content) {
        return new DefaultHttp3DataFrame(content);
    }

    @Override
    public Http3DataFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public Http3DataFrame retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public Http3DataFrame touch() {
        super.touch();
        return this;
    }

    @Override
    public Http3DataFrame touch(Object hint) {
        super.touch(hint);
        return this;
    }
}
