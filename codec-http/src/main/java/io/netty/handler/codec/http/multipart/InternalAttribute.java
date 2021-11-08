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
import io.netty.buffer.api.CompositeBuffer;
import io.netty.buffer.api.Send;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * This Attribute is only for Encoder use to insert special command between object if needed
 * (like Multipart Mixed mode)
 */
final class InternalAttribute implements InterfaceHttpData<InternalAttribute> {
    private final List<Buffer> value;
    private final Charset charset;
    private final BufferAllocator allocator;
    private int size;

    InternalAttribute(Charset charset, BufferAllocator allocator) {
        this.charset = charset;
        this.allocator = allocator;
        value = new ArrayList<>();
    }

    private InternalAttribute(List<Buffer> value, Charset charset, BufferAllocator allocator) {
        this.value = value;
        this.charset = charset;
        this.allocator = allocator;
    }

    @Override
    public HttpDataType getHttpDataType() {
        return HttpDataType.InternalAttribute;
    }

    public void addValue(String value) {
        requireNonNull(value, "value");
        Buffer buf = allocator.allocate(value.length()).writeCharSequence(value, charset);
        this.value.add(buf);
        size += buf.readableBytes();
    }

    public void addValue(String value, int rank) {
        requireNonNull(value, "value");
        Buffer buf = allocator.allocate(value.length()).writeCharSequence(value, charset);
        this.value.add(rank, buf);
        size += buf.readableBytes();
    }

    public void setValue(String value, int rank) {
        requireNonNull(value, "value");
        Buffer buf = allocator.allocate(value.length()).writeCharSequence(value, charset);
        Buffer old = this.value.set(rank, buf);
        if (old != null) {
            size -= old.readableBytes();
            old.close();
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
        for (Buffer elt : value) {
            result.append(elt.toString(charset));
        }
        return result.toString();
    }

    public int size() {
        return size;
    }

    List<Buffer> values() {
        return value;
    }

    @Override
    public String getName() {
        return "InternalAttribute";
    }

    @Override
    public Send<InternalAttribute> send() {
        List<Send<Buffer>> sends = new ArrayList<>(value.size());
        for (Buffer buffer : value) {
            sends.add(buffer.send());
        }
        return new Send<>() {
            @Override
            public InternalAttribute receive() {
                List<Buffer> values = new ArrayList<>(sends.size());
                for (Send<Buffer> send : sends) {
                    values.add(send.receive());
                }
                return new InternalAttribute(values, charset, allocator);
            }

            @Override
            public void close() {
                for (Send<Buffer> send : sends) {
                    send.close();
                }
            }

            @Override
            public boolean referentIsInstanceOf(Class<?> cls) {
                return cls.isAssignableFrom(InternalAttribute.class);
            }
        };
    }

    @Override
    public void close() {
        for (Buffer buffer : value) {
            buffer.close();
        }
    }

    @Override
    public boolean isAccessible() {
        for (Buffer buffer : value) {
            if (!buffer.isAccessible()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public InternalAttribute touch(Object hint) {
        for (Buffer buffer : value) {
            buffer.touch(hint);
        }
        return this;
    }
}
