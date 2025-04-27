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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

public final class DefaultQuicStreamFrame extends DefaultByteBufHolder implements QuicStreamFrame {

    private final boolean fin;

    public DefaultQuicStreamFrame(ByteBuf data, boolean fin) {
        super(data);
        this.fin = fin;
    }

    @Override
    public boolean hasFin() {
        return fin;
    }

    @Override
    public QuicStreamFrame copy() {
        return new DefaultQuicStreamFrame(content().copy(), fin);
    }

    @Override
    public QuicStreamFrame duplicate() {
        return new DefaultQuicStreamFrame(content().duplicate(), fin);
    }

    @Override
    public QuicStreamFrame retainedDuplicate() {
        return new DefaultQuicStreamFrame(content().retainedDuplicate(), fin);
    }

    @Override
    public QuicStreamFrame replace(ByteBuf content) {
        return new DefaultQuicStreamFrame(content, fin);
    }

    @Override
    public QuicStreamFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public QuicStreamFrame retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public QuicStreamFrame touch() {
        super.touch();
        return this;
    }

    @Override
    public QuicStreamFrame touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public String toString() {
        return "DefaultQuicStreamFrame{" +
                "fin=" + fin +
                ", content=" + contentToString() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultQuicStreamFrame that = (DefaultQuicStreamFrame) o;

        if (fin != that.fin) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (fin ? 1 : 0);
        return result;
    }
}
