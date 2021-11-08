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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import static java.util.Objects.requireNonNull;

/**
 * Mixed implementation using both in Memory and in File with a limit of size
 */
public class MixedAttribute implements Attribute<MixedAttribute> {
    private final String baseDir;
    private final boolean deleteOnExit;
    private final BufferAllocator allocator;
    private final long limitSize;
    private Attribute<?> attribute;
    private long maxSize = DefaultHttpDataFactory.MAXSIZE;

    public MixedAttribute(BufferAllocator allocator, String name, long limitSize) {
        this(allocator, name, limitSize, HttpConstants.DEFAULT_CHARSET);
    }

    public MixedAttribute(BufferAllocator allocator, String name, long definedSize, long limitSize) {
        this(allocator, name, definedSize, limitSize, HttpConstants.DEFAULT_CHARSET);
    }

    public MixedAttribute(BufferAllocator allocator, String name, long limitSize, Charset charset) {
        this(allocator, name, limitSize, charset, DiskAttribute.baseDirectory, DiskAttribute.deleteOnExitTemporaryFile);
    }

    public MixedAttribute(BufferAllocator allocator, String name, long limitSize, Charset charset, String baseDir,
                          boolean deleteOnExit) {
        this.allocator = requireNonNull(allocator, "allocator");
        this.limitSize = limitSize;
        attribute = new MemoryAttribute(allocator, name, charset);
        this.baseDir = baseDir;
        this.deleteOnExit = deleteOnExit;
    }

    public MixedAttribute(BufferAllocator allocator, String name, long definedSize, long limitSize, Charset charset) {
        this(allocator, name, definedSize, limitSize, charset,
                DiskAttribute.baseDirectory, DiskAttribute.deleteOnExitTemporaryFile);
    }

    public MixedAttribute(BufferAllocator allocator, String name, long definedSize, long limitSize, Charset charset,
                          String baseDir, boolean deleteOnExit) {
        this.allocator = requireNonNull(allocator, "allocator");
        this.limitSize = limitSize;
        attribute = new MemoryAttribute(allocator, name, definedSize, charset);
        this.baseDir = baseDir;
        this.deleteOnExit = deleteOnExit;
    }

    public MixedAttribute(BufferAllocator allocator, String name, String value, long limitSize) {
        this(allocator, name, value, limitSize, HttpConstants.DEFAULT_CHARSET,
                DiskAttribute.baseDirectory, DiskFileUpload.deleteOnExitTemporaryFile);
    }

    public MixedAttribute(BufferAllocator allocator, String name, String value, long limitSize, Charset charset) {
        this(allocator, name, value, limitSize, charset,
                DiskAttribute.baseDirectory, DiskFileUpload.deleteOnExitTemporaryFile);
    }

    public MixedAttribute(BufferAllocator allocator, String name, String value, long limitSize, Charset charset,
                          String baseDir, boolean deleteOnExit) {
        this.allocator = requireNonNull(allocator, "allocator");
        this.limitSize = limitSize;
        if (value.length() > this.limitSize) {
            try {
                attribute = new DiskAttribute(allocator, name, value, charset, baseDir, deleteOnExit);
            } catch (IOException e) {
                // revert to Memory mode
                try {
                    attribute = new MemoryAttribute(allocator, name, value, charset);
                } catch (IOException ignore) {
                    throw new IllegalArgumentException(e);
                }
            }
        } else {
            try {
                attribute = new MemoryAttribute(allocator, name, value, charset);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
        this.baseDir = baseDir;
        this.deleteOnExit = deleteOnExit;
    }

    private MixedAttribute(MixedAttribute mixedAttribute, Attribute<?> attribute) {
        this.allocator = mixedAttribute.allocator;
        this.baseDir = mixedAttribute.baseDir;
        this.deleteOnExit = mixedAttribute.deleteOnExit;
        this.attribute = attribute;
        this.limitSize = mixedAttribute.limitSize;
        this.maxSize = mixedAttribute.maxSize;
    }

    @Override
    public long getMaxSize() {
        return maxSize;
    }

    @Override
    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
        attribute.setMaxSize(maxSize);
    }

    @Override
    public void checkSize(long newSize) throws IOException {
        if (maxSize >= 0 && newSize > maxSize) {
            throw new IOException("Size exceed allowed maximum capacity");
        }
    }

    @Override
    public void addContent(Buffer buffer, boolean last) throws IOException {
        if (attribute instanceof MemoryAttribute) {
            try {
                checkSize(attribute.length() + buffer.readableBytes());
                if (attribute.length() + buffer.readableBytes() > limitSize) {
                    DiskAttribute diskAttribute = new DiskAttribute(allocator, attribute
                            .getName(), attribute.definedLength(), baseDir, deleteOnExit);
                    diskAttribute.setMaxSize(maxSize);
                    if (((MemoryAttribute) attribute).getBuffer() != null) {
                        diskAttribute.addContent(((MemoryAttribute) attribute)
                            .getBuffer(), false);
                    }
                    attribute = diskAttribute;
                }
            } catch (IOException e) {
                buffer.close();
                throw e;
            }
        }
        attribute.addContent(buffer, last);
    }

    @Override
    public void delete() {
        attribute.delete();
    }

    @Override
    public byte[] get() throws IOException {
        return attribute.get();
    }

    @Override
    public Buffer getBuffer() throws IOException {
        return attribute.getBuffer();
    }

    @Override
    public Charset getCharset() {
        return attribute.getCharset();
    }

    @Override
    public String getString() throws IOException {
        return attribute.getString();
    }

    @Override
    public String getString(Charset encoding) throws IOException {
        return attribute.getString(encoding);
    }

    @Override
    public boolean isCompleted() {
        return attribute.isCompleted();
    }

    @Override
    public boolean isInMemory() {
        return attribute.isInMemory();
    }

    @Override
    public long length() {
        return attribute.length();
    }

    @Override
    public long definedLength() {
        return attribute.definedLength();
    }

    @Override
    public boolean renameTo(File dest) throws IOException {
        return attribute.renameTo(dest);
    }

    @Override
    public void setCharset(Charset charset) {
        attribute.setCharset(charset);
    }

    @Override
    public void setContent(Buffer buffer) throws IOException {
        try {
            checkSize(buffer.readableBytes());
        } catch (IOException e) {
            buffer.close();
            throw e;
        }
        if (buffer.readableBytes() > limitSize) {
            if (attribute instanceof MemoryAttribute) {
                // change to Disk
                attribute = new DiskAttribute(allocator, attribute.getName(), attribute.definedLength(), baseDir,
                        deleteOnExit);
                attribute.setMaxSize(maxSize);
            }
        }
        attribute.setContent(buffer);
    }

    @Override
    public void setContent(File file) throws IOException {
        checkSize(file.length());
        if (file.length() > limitSize) {
            if (attribute instanceof MemoryAttribute) {
                // change to Disk
                attribute = new DiskAttribute(allocator, attribute.getName(), attribute.definedLength(), baseDir,
                        deleteOnExit);
                attribute.setMaxSize(maxSize);
            }
        }
        attribute.setContent(file);
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
        if (attribute instanceof MemoryAttribute) {
            // change to Disk even if we don't know the size
            attribute = new DiskAttribute(allocator, attribute.getName(), attribute.definedLength(), baseDir,
                    deleteOnExit);
            attribute.setMaxSize(maxSize);
        }
        attribute.setContent(inputStream);
    }

    @Override
    public HttpDataType getHttpDataType() {
        return attribute.getHttpDataType();
    }

    @Override
    public String getName() {
        return attribute.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MixedAttribute that = (MixedAttribute) o;

        return attribute != null ? attribute.equals(that.attribute) : that.attribute == null;
    }

    @Override
    public int hashCode() {
        return attribute != null ? attribute.hashCode() : 0;
    }

    @Override
    public int compareTo(InterfaceHttpData o) {
        return attribute.compareTo(o);
    }

    @Override
    public String toString() {
        return "Mixed: " + attribute;
    }

    @Override
    public String getValue() throws IOException {
        return attribute.getValue();
    }

    @Override
    public void setValue(String value) throws IOException {
        attribute.setValue(value);
    }

    @Override
    public Buffer getChunk(int length) throws IOException {
        return attribute.getChunk(length);
    }

    @Override
    public File getFile() throws IOException {
        return attribute.getFile();
    }

    @Override
    public Send<MixedAttribute> send() {
        return attribute.send().map(MixedAttribute.class, a -> new MixedAttribute(this, a));
    }

    @Override
    public void close() {
        attribute.close();
    }

    @Override
    public boolean isAccessible() {
        return attribute.isAccessible();
    }

    @Override
    public MixedAttribute touch(Object hint) {
        attribute.touch(hint);
        return this;
    }
}
