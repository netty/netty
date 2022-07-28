/*
 * Copyright 2022 The Netty Project
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
import io.netty.util.AbstractReferenceCounted;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

abstract class AbstractMixedHttpData<D extends HttpData> extends AbstractReferenceCounted implements HttpData {
    final String baseDir;
    final boolean deleteOnExit;
    D wrapped;

    private final long limitSize;

    AbstractMixedHttpData(long limitSize, String baseDir, boolean deleteOnExit, D initial) {
        this.limitSize = limitSize;
        this.wrapped = initial;
        this.baseDir = baseDir;
        this.deleteOnExit = deleteOnExit;
    }

    abstract D makeDiskData();

    @Override
    public long getMaxSize() {
        return wrapped.getMaxSize();
    }

    @Override
    public void setMaxSize(long maxSize) {
        wrapped.setMaxSize(maxSize);
    }

    @Override
    public ByteBuf content() {
        return wrapped.content();
    }

    @Override
    public void checkSize(long newSize) throws IOException {
        wrapped.checkSize(newSize);
    }

    @Override
    public long definedLength() {
        return wrapped.definedLength();
    }

    @Override
    public Charset getCharset() {
        return wrapped.getCharset();
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }

    @Override
    public void addContent(ByteBuf buffer, boolean last) throws IOException {
        if (wrapped instanceof AbstractMemoryHttpData) {
            try {
                checkSize(wrapped.length() + buffer.readableBytes());
                if (wrapped.length() + buffer.readableBytes() > limitSize) {
                    D diskData = makeDiskData();
                    ByteBuf data = ((AbstractMemoryHttpData) wrapped).getByteBuf();
                    if (data != null && data.isReadable()) {
                        diskData.addContent(data.retain(), false);
                    }
                    wrapped.release();
                    wrapped = diskData;
                }
            } catch (IOException e) {
                buffer.release();
                throw e;
            }
        }
        wrapped.addContent(buffer, last);
    }

    @Override
    protected void deallocate() {
        delete();
    }

    @Override
    public void delete() {
        wrapped.delete();
    }

    @Override
    public byte[] get() throws IOException {
        return wrapped.get();
    }

    @Override
    public ByteBuf getByteBuf() throws IOException {
        return wrapped.getByteBuf();
    }

    @Override
    public String getString() throws IOException {
        return wrapped.getString();
    }

    @Override
    public String getString(Charset encoding) throws IOException {
        return wrapped.getString(encoding);
    }

    @Override
    public boolean isInMemory() {
        return wrapped.isInMemory();
    }

    @Override
    public long length() {
        return wrapped.length();
    }

    @Override
    public boolean renameTo(File dest) throws IOException {
        return wrapped.renameTo(dest);
    }

    @Override
    public void setCharset(Charset charset) {
        wrapped.setCharset(charset);
    }

    @Override
    public void setContent(ByteBuf buffer) throws IOException {
        try {
            checkSize(buffer.readableBytes());
        } catch (IOException e) {
            buffer.release();
            throw e;
        }
        if (buffer.readableBytes() > limitSize) {
            if (wrapped instanceof AbstractMemoryHttpData) {
                // change to Disk
                wrapped.release();
                wrapped = makeDiskData();
            }
        }
        wrapped.setContent(buffer);
    }

    @Override
    public void setContent(File file) throws IOException {
        checkSize(file.length());
        if (file.length() > limitSize) {
            if (wrapped instanceof AbstractMemoryHttpData) {
                // change to Disk
                wrapped.release();
                wrapped = makeDiskData();
            }
        }
        wrapped.setContent(file);
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
        if (wrapped instanceof AbstractMemoryHttpData) {
            // change to Disk even if we don't know the size
            wrapped.release();
            wrapped = makeDiskData();
        }
        wrapped.setContent(inputStream);
    }

    @Override
    public boolean isCompleted() {
        return wrapped.isCompleted();
    }

    @Override
    public HttpDataType getHttpDataType() {
        return wrapped.getHttpDataType();
    }

    @Override
    public int hashCode() {
        return wrapped.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return wrapped.equals(obj);
    }

    @Override
    public int compareTo(InterfaceHttpData o) {
        return wrapped.compareTo(o);
    }

    @Override
    public String toString() {
        return "Mixed: " + wrapped;
    }

    @Override
    public ByteBuf getChunk(int length) throws IOException {
        return wrapped.getChunk(length);
    }

    @Override
    public File getFile() throws IOException {
        return wrapped.getFile();
    }

    @SuppressWarnings("unchecked")
    @Override
    public D copy() {
        return (D) wrapped.copy();
    }

    @SuppressWarnings("unchecked")
    @Override
    public D duplicate() {
        return (D) wrapped.duplicate();
    }

    @SuppressWarnings("unchecked")
    @Override
    public D retainedDuplicate() {
        return (D) wrapped.retainedDuplicate();
    }

    @SuppressWarnings("unchecked")
    @Override
    public D replace(ByteBuf content) {
        return (D) wrapped.replace(content);
    }

    @SuppressWarnings("unchecked")
    @Override
    public D touch() {
        wrapped.touch();
        return (D) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public D touch(Object hint) {
        wrapped.touch(hint);
        return (D) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public D retain() {
        return (D) super.retain();
    }

    @SuppressWarnings("unchecked")
    @Override
    public D retain(int increment) {
        return (D) super.retain(increment);
    }
}
