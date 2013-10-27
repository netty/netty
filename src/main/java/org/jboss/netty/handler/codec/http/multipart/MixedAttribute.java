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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Mixed implementation using both in Memory and in File with a limit of size
 */
public class MixedAttribute implements Attribute {
    private Attribute attribute;

    private final long limitSize;
    protected long maxSize = DefaultHttpDataFactory.MAXSIZE;

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

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
        attribute.setMaxSize(maxSize);
    }

    public void checkSize(long newSize) throws IOException {
        if (maxSize >= 0 && newSize > maxSize) {
            throw new IOException("Size exceed allowed maximum capacity");
        }
    }

    public void addContent(ChannelBuffer buffer, boolean last) throws IOException {
        if (attribute instanceof MemoryAttribute) {
            checkSize(attribute.length() + buffer.readableBytes());
            if (attribute.length() + buffer.readableBytes() > limitSize) {
                DiskAttribute diskAttribute = new DiskAttribute(attribute
                        .getName());
                diskAttribute.setMaxSize(maxSize);
                if (((MemoryAttribute) attribute).getChannelBuffer() != null) {
                    diskAttribute.addContent(((MemoryAttribute) attribute)
                        .getChannelBuffer(), false);
                }
                attribute = diskAttribute;
            }
        }
        attribute.addContent(buffer, last);
    }

    public void delete() {
        attribute.delete();
    }

    public byte[] get() throws IOException {
        return attribute.get();
    }

    public ChannelBuffer getChannelBuffer() throws IOException {
        return attribute.getChannelBuffer();
    }

    public Charset getCharset() {
        return attribute.getCharset();
    }

    public String getString() throws IOException {
        return attribute.getString();
    }

    public String getString(Charset encoding) throws IOException {
        return attribute.getString(encoding);
    }

    public boolean isCompleted() {
        return attribute.isCompleted();
    }

    public boolean isInMemory() {
        return attribute.isInMemory();
    }

    public long length() {
        return attribute.length();
    }

    public boolean renameTo(File dest) throws IOException {
        return attribute.renameTo(dest);
    }

    public void setCharset(Charset charset) {
        attribute.setCharset(charset);
    }

    public void setContent(ChannelBuffer buffer) throws IOException {
        checkSize(buffer.readableBytes());
        if (buffer.readableBytes() > limitSize) {
            if (attribute instanceof MemoryAttribute) {
                // change to Disk
                attribute = new DiskAttribute(attribute.getName());
                attribute.setMaxSize(maxSize);
            }
        }
        attribute.setContent(buffer);
    }

    public void setContent(File file) throws IOException {
        checkSize(file.length());
        if (file.length() > limitSize) {
            if (attribute instanceof MemoryAttribute) {
                // change to Disk
                attribute = new DiskAttribute(attribute.getName());
                attribute.setMaxSize(maxSize);
            }
        }
        attribute.setContent(file);
    }

    public void setContent(InputStream inputStream) throws IOException {
        if (attribute instanceof MemoryAttribute) {
            // change to Disk even if we don't know the size
            attribute = new DiskAttribute(attribute.getName());
            attribute.setMaxSize(maxSize);
        }
        attribute.setContent(inputStream);
    }

    public HttpDataType getHttpDataType() {
        return attribute.getHttpDataType();
    }

    public String getName() {
        return attribute.getName();
    }

    public int compareTo(InterfaceHttpData o) {
        return attribute.compareTo(o);
    }

    @Override
    public String toString() {
        return "Mixed: " + attribute.toString();
    }

    public String getValue() throws IOException {
        return attribute.getValue();
    }

    public void setValue(String value) throws IOException {
        if (value != null) {
            checkSize(value.getBytes().length);
        }
        attribute.setValue(value);
    }

    public ChannelBuffer getChunk(int length) throws IOException {
        return attribute.getChunk(length);
    }

    public File getFile() throws IOException {
        return attribute.getFile();
    }

}
