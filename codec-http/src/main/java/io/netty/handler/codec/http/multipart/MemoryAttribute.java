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
package io.netty.handler.codec.http.multipart;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelException;
import io.netty.handler.codec.http.HttpConstants;

import java.io.IOException;

import static io.netty.buffer.Unpooled.*;

/**
 * Memory implementation of Attributes
 */
public class MemoryAttribute extends AbstractMemoryHttpData implements Attribute {

    public MemoryAttribute(String name) {
        super(name, HttpConstants.DEFAULT_CHARSET, 0);
    }

    public MemoryAttribute(String name, String value) throws IOException {
        super(name, HttpConstants.DEFAULT_CHARSET, 0); // Attribute have no default size
        setValue(value);
    }

    @Override
    public HttpDataType getHttpDataType() {
        return HttpDataType.Attribute;
    }

    @Override
    public String getValue() {
        return getByteBuf().toString(charset);
    }

    @Override
    public void setValue(String value) throws IOException {
        if (value == null) {
            throw new NullPointerException("value");
        }
        byte [] bytes = value.getBytes(charset.name());
        ByteBuf buffer = wrappedBuffer(bytes);
        if (definedSize > 0) {
            definedSize = buffer.readableBytes();
        }
        setContent(buffer);
    }

    @Override
    public void addContent(ByteBuf buffer, boolean last) throws IOException {
        int localsize = buffer.readableBytes();
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
        MemoryAttribute attr = new MemoryAttribute(getName());
        attr.setCharset(getCharset());
        ByteBuf content = content();
        if (content != null) {
            try {
                attr.setContent(content.copy());
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }
        return attr;
    }

    @Override
    public Attribute duplicate() {
        MemoryAttribute attr = new MemoryAttribute(getName());
        attr.setCharset(getCharset());
        ByteBuf content = content();
        if (content != null) {
            try {
                attr.setContent(content.duplicate());
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
}
