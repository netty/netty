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
import io.netty.util.internal.StringUtil;

import java.util.Objects;

public final class DefaultHttp3UnknownFrame extends DefaultByteBufHolder implements Http3UnknownFrame {
    private final long type;

    public DefaultHttp3UnknownFrame(long type, ByteBuf payload) {
        super(payload);
        this.type = Http3CodecUtils.checkIsReservedFrameType(type);
    }

    @Override
    public long type() {
        return type;
    }

    @Override
    public Http3UnknownFrame copy() {
        return new DefaultHttp3UnknownFrame(type, content().copy());
    }

    @Override
    public Http3UnknownFrame duplicate() {
        return new DefaultHttp3UnknownFrame(type, content().duplicate());
    }

    @Override
    public Http3UnknownFrame retainedDuplicate() {
        return new DefaultHttp3UnknownFrame(type, content().retainedDuplicate());
    }

    @Override
    public Http3UnknownFrame replace(ByteBuf content) {
        return new DefaultHttp3UnknownFrame(type, content);
    }

    @Override
    public Http3UnknownFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public Http3UnknownFrame retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public Http3UnknownFrame touch() {
        super.touch();
        return this;
    }

    @Override
    public Http3UnknownFrame touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(type=" + type() + ", content=" + content() + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultHttp3UnknownFrame that = (DefaultHttp3UnknownFrame) o;
        if (type != that.type) {
            return false;
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), type);
    }
}
