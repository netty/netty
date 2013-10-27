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
package org.jboss.netty.handler.codec.http.multipart;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpConstants;

import java.io.IOException;

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

    public HttpDataType getHttpDataType() {
        return HttpDataType.Attribute;
    }

    public String getValue() {
        return getChannelBuffer().toString(charset);
    }

    public void setValue(String value) throws IOException {
        if (value == null) {
            throw new NullPointerException("value");
        }
        byte [] bytes = value.getBytes(charset.name());
        checkSize(bytes.length);
        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(bytes);
        if (definedSize > 0) {
            definedSize = buffer.readableBytes();
        }
        setContent(buffer);
    }

    @Override
    public void addContent(ChannelBuffer buffer, boolean last) throws IOException {
        int localsize = buffer.readableBytes();
        checkSize(size + localsize);
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

}
