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
import io.netty.channel.ChannelException;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.util.internal.ObjectUtil;

import java.io.IOException;
import java.nio.charset.Charset;

import static io.netty.buffer.Unpooled.*;

/**
 * Memory implementation of Attributes
 */
public class MemoryAttribute extends AbstractMemoryHttpData implements Attribute {

    public MemoryAttribute(String name) {
        this(name, HttpConstants.DEFAULT_CHARSET);
    }

    public MemoryAttribute(String name, long definedSize) {
        this(name, definedSize, HttpConstants.DEFAULT_CHARSET);
    }

    public MemoryAttribute(String name, Charset charset) {
        super(name, charset, 0);
    }

    public MemoryAttribute(String name, long definedSize, Charset charset) {
        super(name, charset, definedSize);
    }

    public MemoryAttribute(String name, String value) throws IOException {
        this(name, value, HttpConstants.DEFAULT_CHARSET); // Attribute have no default size
    }

    public MemoryAttribute(String name, String value, Charset charset) throws IOException {
        super(name, charset, 0); // Attribute have no default size
        setValue(value);
    }

    @Override
    public HttpDataType getHttpDataType() {
        return HttpDataType.Attribute;
    }

    @Override
    public String getValue() {
        return getByteBuf().toString(getCharset());
    }

    @Override
    public void setValue(String value) throws IOException {
        ObjectUtil.checkNotNull(value, "value");
        byte [] bytes = value.getBytes(getCharset());
        checkSize(bytes.length);
        ByteBuf buffer = wrappedBuffer(bytes);
        if (definedSize > 0) {
            definedSize = buffer.readableBytes();
        }
        setContent(buffer);
    }

    @Override
    public void addContent(ByteBuf buffer, boolean last) throws IOException {
        int localsize = buffer.readableBytes();
        try {
            checkSize(size + localsize);
        } catch (IOException e) {
            buffer.release();
            throw e;
        }
        if (definedSize > 0 && definedSize < size + localsize) {
            definedSize = size + localsize;
        }
        super.addContent(buffer, last);
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Attribute)) {
            return false;
        }
        Attribute attribute = (Attribute) o;
        return getName().equalsIgnoreCase(attribute.getName());
    }

    @Override
    public int compareTo(InterfaceHttpData other) {
        if (!(other instanceof Attribute)) {
            throw new ClassCastException("Cannot compare " + getHttpDataType() +
                    " with " + other.getHttpDataType());
        }
        return compareTo((Attribute) other);
    }

    public int compareTo(Attribute o) {
        return getName().compareToIgnoreCase(o.getName());
    }

    @Override
    public String toString() {
        return getName() + '=' + getValue();
    }

    @Override
    public Attribute copy() {
        final ByteBuf content = content();
        return replace(content != null ? content.copy() : null);
    }

    @Override
    public Attribute duplicate() {
        final ByteBuf content = content();
        return replace(content != null ? content.duplicate() : null);
    }

    @Override
    public Attribute retainedDuplicate() {
        ByteBuf content = content();
        if (content != null) {
            content = content.retainedDuplicate();
            boolean success = false;
            try {
                Attribute duplicate = replace(content);
                success = true;
                return duplicate;
            } finally {
                if (!success) {
                    content.release();
                }
            }
        } else {
            return replace(null);
        }
    }

    @Override
    public Attribute replace(ByteBuf content) {
        MemoryAttribute attr = new MemoryAttribute(getName());
        attr.setCharset(getCharset());
        if (content != null) {
            try {
                attr.setContent(content);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }
        return attr;
    }

    @Override
    public Attribute retain() {
        super.retain();
        return this;
    }

    @Override
    public Attribute retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public Attribute touch() {
        super.touch();
        return this;
    }

    @Override
    public Attribute touch(Object hint) {
        super.touch(hint);
        return this;
    }
}
