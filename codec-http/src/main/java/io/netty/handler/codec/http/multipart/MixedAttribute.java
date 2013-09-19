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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Mixed implementation using both in Memory and in File with a limit of size
 */
public class MixedAttribute implements Attribute {
    private Attribute attribute;

    private final long limitSize;

    public MixedAttribute(String name, long limitSize) {
        this.limitSize = limitSize;
        attribute = new MemoryAttribute(name);
    }

    public MixedAttribute(String name, String value, long limitSize) {
        this.limitSize = limitSize;
        if (value.length() > this.limitSize) {
            try {
                attribute = new DiskAttribute(name, value);
            } catch (IOException e) {
                // revert to Memory mode
                try {
                    attribute = new MemoryAttribute(name, value);
                } catch (IOException e1) {
                    throw new IllegalArgumentException(e);
                }
            }
        } else {
            try {
                attribute = new MemoryAttribute(name, value);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    @Override
    public void addContent(ByteBuf buffer, boolean last) throws IOException {
        if (attribute instanceof MemoryAttribute) {
            if (attribute.length() + buffer.readableBytes() > limitSize) {
                DiskAttribute diskAttribute = new DiskAttribute(attribute
                        .getName());
                if (((MemoryAttribute) attribute).getByteBuf() != null) {
                    diskAttribute.addContent(((MemoryAttribute) attribute)
                        .getByteBuf(), false);
                }
                attribute = diskAttribute;
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
    public ByteBuf getByteBuf() throws IOException {
        return attribute.getByteBuf();
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
    public boolean renameTo(File dest) throws IOException {
        return attribute.renameTo(dest);
    }

    @Override
    public void setCharset(Charset charset) {
        attribute.setCharset(charset);
    }

    @Override
    public void setContent(ByteBuf buffer) throws IOException {
        if (buffer.readableBytes() > limitSize) {
            if (attribute instanceof MemoryAttribute) {
                // change to Disk
                attribute = new DiskAttribute(attribute.getName());
            }
        }
        attribute.setContent(buffer);
    }

    @Override
    public void setContent(File file) throws IOException {
        if (file.length() > limitSize) {
            if (attribute instanceof MemoryAttribute) {
                // change to Disk
                attribute = new DiskAttribute(attribute.getName());
            }
        }
        attribute.setContent(file);
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
        if (attribute instanceof MemoryAttribute) {
            // change to Disk even if we don't know the size
            attribute = new DiskAttribute(attribute.getName());
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
    public int compareTo(InterfaceHttpData o) {
        return attribute.compareTo(o);
    }

    @Override
    public String toString() {
        return "Mixed: " + attribute.toString();
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
    public ByteBuf getChunk(int length) throws IOException {
        return attribute.getChunk(length);
    }

    @Override
    public File getFile() throws IOException {
        return attribute.getFile();
    }

    @Override
    public Attribute copy() {
        return attribute.copy();
    }

    @Override
    public Attribute duplicate() {
        return attribute.duplicate();
    }

    @Override
    public ByteBuf content() {
        return attribute.content();
    }

    @Override
    public int refCnt() {
        return attribute.refCnt();
    }

    @Override
    public Attribute retain() {
        attribute.retain();
        return this;
    }

    @Override
    public Attribute retain(int increment) {
        attribute.retain(increment);
        return this;
    }

    @Override
    public boolean release() {
        return attribute.release();
    }

    @Override
    public boolean release(int decrement) {
        return attribute.release(decrement);
    }
}
