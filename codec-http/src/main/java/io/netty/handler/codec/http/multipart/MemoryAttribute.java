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

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.Send;
import io.netty.handler.codec.http.HttpConstants;

import java.io.IOException;
import java.nio.charset.Charset;

import static java.util.Objects.requireNonNull;

/**
 * Memory implementation of Attributes
 */
public class MemoryAttribute extends AbstractMemoryHttpData<MemoryAttribute> implements Attribute<MemoryAttribute> {
    private boolean closed;

    public MemoryAttribute(BufferAllocator allocator, String name) {
        this(allocator, name, HttpConstants.DEFAULT_CHARSET);
    }

    public MemoryAttribute(BufferAllocator allocator, String name, long definedSize) {
        this(allocator, name, definedSize, HttpConstants.DEFAULT_CHARSET);
    }

    public MemoryAttribute(BufferAllocator allocator, String name, Charset charset) {
        super(allocator, name, charset, 0);
    }

    public MemoryAttribute(BufferAllocator allocator, String name, long definedSize, Charset charset) {
        super(allocator, name, charset, definedSize);
    }

    public MemoryAttribute(BufferAllocator allocator, String name, String value) throws IOException {
        this(allocator, name, value, HttpConstants.DEFAULT_CHARSET); // Attribute have no default size
    }

    public MemoryAttribute(BufferAllocator allocator, String name, String value, Charset charset) throws IOException {
        super(allocator, name, charset, 0); // Attribute have no default size
        setValue(value);
    }

    @Override
    public HttpDataType getHttpDataType() {
        return HttpDataType.Attribute;
    }

    @Override
    public String getValue() {
        return getBuffer().toString(getCharset());
    }

    @Override
    public void setValue(String value) throws IOException {
        requireNonNull(value, "value");
        byte [] bytes = value.getBytes(getCharset());
        checkSize(bytes.length);
        Buffer buffer = allocator().allocate(bytes.length).writeBytes(bytes);
        if (definedSize > 0) {
            definedSize = buffer.readableBytes();
        }
        setContent(buffer);
    }

    @Override
    public void addContent(Buffer buffer, boolean last) throws IOException {
        int localsize = buffer.readableBytes();
        try {
            checkSize(size + localsize);
        } catch (IOException e) {
            buffer.close();
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
        Attribute<?> attribute = (Attribute<?>) o;
        return getName().equalsIgnoreCase(attribute.getName());
    }

    @Override
    public int compareTo(InterfaceHttpData other) {
        if (!(other instanceof Attribute)) {
            throw new ClassCastException("Cannot compare " + getHttpDataType() +
                    " with " + other.getHttpDataType());
        }
        return compareTo((Attribute<?>) other);
    }

    public int compareTo(Attribute<?> o) {
        return getName().compareToIgnoreCase(o.getName());
    }

    @Override
    public String toString() {
        return getName() + '=' + getValue();
    }

    @Override
    public Send<MemoryAttribute> send() {
        return Send.sending(MemoryAttribute.class, () -> this);
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean isAccessible() {
        return !closed;
    }

    @Override
    public MemoryAttribute touch(Object hint) {
        return this;
    }
}
