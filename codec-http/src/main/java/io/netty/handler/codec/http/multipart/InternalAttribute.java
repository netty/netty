/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http.multipart;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.internal.ObjectUtil;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * This Attribute is only for Encoder use to insert special command between object if needed
 * (like Multipart Mixed mode)
 */
final class InternalAttribute extends AbstractReferenceCounted implements InterfaceHttpData {
    private final List<ByteBuf> value = new ArrayList<ByteBuf>();
    private final Charset charset;
    private int size;

    InternalAttribute(Charset charset) {
        this.charset = charset;
    }

    @Override
    public HttpDataType getHttpDataType() {
        return HttpDataType.InternalAttribute;
    }

    public void addValue(String value) {
        ObjectUtil.checkNotNull(value, "value");
        ByteBuf buf = Unpooled.copiedBuffer(value, charset);
        this.value.add(buf);
        size += buf.readableBytes();
    }

    public void addValue(String value, int rank) {
        ObjectUtil.checkNotNull(value, "value");
        ByteBuf buf = Unpooled.copiedBuffer(value, charset);
        this.value.add(rank, buf);
        size += buf.readableBytes();
    }

    public void setValue(String value, int rank) {
        ObjectUtil.checkNotNull(value, "value");
        ByteBuf buf = Unpooled.copiedBuffer(value, charset);
        ByteBuf old = this.value.set(rank, buf);
        if (old != null) {
            size -= old.readableBytes();
            old.release();
        }
        size += buf.readableBytes();
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InternalAttribute)) {
            return false;
        }
        InternalAttribute attribute = (InternalAttribute) o;
        return getName().equalsIgnoreCase(attribute.getName());
    }

    @Override
    public int compareTo(InterfaceHttpData o) {
        if (!(o instanceof InternalAttribute)) {
            throw new ClassCastException("Cannot compare " + getHttpDataType() +
                    " with " + o.getHttpDataType());
        }
        return compareTo((InternalAttribute) o);
    }

    public int compareTo(InternalAttribute o) {
        return getName().compareToIgnoreCase(o.getName());
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (ByteBuf elt : value) {
            result.append(elt.toString(charset));
        }
        return result.toString();
    }

    public int size() {
        return size;
    }

    public ByteBuf toByteBuf() {
        return Unpooled.compositeBuffer().addComponents(value).writerIndex(size()).readerIndex(0);
    }

    @Override
    public String getName() {
        return "InternalAttribute";
    }

    @Override
    protected void deallocate() {
        // Do nothing
    }

    @Override
    public InterfaceHttpData retain() {
        for (ByteBuf buf: value) {
            buf.retain();
        }
        return this;
    }

    @Override
    public InterfaceHttpData retain(int increment) {
        for (ByteBuf buf: value) {
            buf.retain(increment);
        }
        return this;
    }

    @Override
    public InterfaceHttpData touch() {
        for (ByteBuf buf: value) {
            buf.touch();
        }
        return this;
    }

    @Override
    public InterfaceHttpData touch(Object hint) {
        for (ByteBuf buf: value) {
            buf.touch(hint);
        }
        return this;
    }
}
