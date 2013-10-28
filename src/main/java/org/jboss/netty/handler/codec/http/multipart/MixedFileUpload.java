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
public class MixedFileUpload implements FileUpload {
    private FileUpload fileUpload;

    private final long limitSize;

    private final long definedSize;
    protected long maxSize = DefaultHttpDataFactory.MAXSIZE;

    public MixedFileUpload(String name, String filename, String contentType,
            String contentTransferEncoding, Charset charset, long size,
            long limitSize) {
        this.limitSize = limitSize;
        if (size > this.limitSize) {
            fileUpload = new DiskFileUpload(name, filename, contentType,
                    contentTransferEncoding, charset, size);
        } else {
            fileUpload = new MemoryFileUpload(name, filename, contentType,
                    contentTransferEncoding, charset, size);
        }
        definedSize = size;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
        fileUpload.setMaxSize(maxSize);
    }

    public void checkSize(long newSize) throws IOException {
        if (maxSize >= 0 && newSize > maxSize) {
            throw new IOException("Size exceed allowed maximum capacity");
        }
    }

    public void addContent(ChannelBuffer buffer, boolean last)
            throws IOException {
        if (fileUpload instanceof MemoryFileUpload) {
            checkSize(fileUpload.length() + buffer.readableBytes());
            if (fileUpload.length() + buffer.readableBytes() > limitSize) {
                DiskFileUpload diskFileUpload = new DiskFileUpload(fileUpload
                        .getName(), fileUpload.getFilename(), fileUpload
                        .getContentType(), fileUpload
                        .getContentTransferEncoding(), fileUpload.getCharset(),
                        definedSize);
                diskFileUpload.setMaxSize(maxSize);
                if (((MemoryFileUpload) fileUpload).getChannelBuffer() != null) {
                    diskFileUpload.addContent(((MemoryFileUpload) fileUpload)
                        .getChannelBuffer(), false);
                }
                fileUpload = diskFileUpload;
            }
        }
        fileUpload.addContent(buffer, last);
    }

    public void delete() {
        fileUpload.delete();
    }

    public byte[] get() throws IOException {
        return fileUpload.get();
    }

    public ChannelBuffer getChannelBuffer() throws IOException {
        return fileUpload.getChannelBuffer();
    }

    public Charset getCharset() {
        return fileUpload.getCharset();
    }

    public String getContentType() {
        return fileUpload.getContentType();
    }

    public String getContentTransferEncoding() {
        return fileUpload.getContentTransferEncoding();
    }

    public String getFilename() {
        return fileUpload.getFilename();
    }

    public String getString() throws IOException {
        return fileUpload.getString();
    }

    public String getString(Charset encoding) throws IOException {
        return fileUpload.getString(encoding);
    }

    public boolean isCompleted() {
        return fileUpload.isCompleted();
    }

    public boolean isInMemory() {
        return fileUpload.isInMemory();
    }

    public long length() {
        return fileUpload.length();
    }

    public boolean renameTo(File dest) throws IOException {
        return fileUpload.renameTo(dest);
    }

    public void setCharset(Charset charset) {
        fileUpload.setCharset(charset);
    }

    public void setContent(ChannelBuffer buffer) throws IOException {
        checkSize(buffer.readableBytes());
        if (buffer.readableBytes() > limitSize) {
            if (fileUpload instanceof MemoryFileUpload) {
                // change to Disk
                fileUpload = new DiskFileUpload(fileUpload
                        .getName(), fileUpload.getFilename(), fileUpload
                        .getContentType(), fileUpload
                        .getContentTransferEncoding(), fileUpload.getCharset(),
                        definedSize);
                fileUpload.setMaxSize(maxSize);
            }
        }
        fileUpload.setContent(buffer);
    }

    public void setContent(File file) throws IOException {
        checkSize(file.length());
        if (file.length() > limitSize) {
            if (fileUpload instanceof MemoryFileUpload) {
                // change to Disk
                fileUpload = new DiskFileUpload(fileUpload
                        .getName(), fileUpload.getFilename(), fileUpload
                        .getContentType(), fileUpload
                        .getContentTransferEncoding(), fileUpload.getCharset(),
                        definedSize);
                fileUpload.setMaxSize(maxSize);
            }
        }
        fileUpload.setContent(file);
    }

    public void setContent(InputStream inputStream) throws IOException {
        if (fileUpload instanceof MemoryFileUpload) {
            // change to Disk
            fileUpload = new DiskFileUpload(fileUpload
                    .getName(), fileUpload.getFilename(), fileUpload
                    .getContentType(), fileUpload
                    .getContentTransferEncoding(), fileUpload.getCharset(),
                    definedSize);
            fileUpload.setMaxSize(maxSize);
        }
        fileUpload.setContent(inputStream);
    }

    public void setContentType(String contentType) {
        fileUpload.setContentType(contentType);
    }

    public void setContentTransferEncoding(String contentTransferEncoding) {
        fileUpload.setContentTransferEncoding(contentTransferEncoding);
    }

    public void setFilename(String filename) {
        fileUpload.setFilename(filename);
    }

    public HttpDataType getHttpDataType() {
        return fileUpload.getHttpDataType();
    }

    public String getName() {
        return fileUpload.getName();
    }

    public int compareTo(InterfaceHttpData o) {
        return fileUpload.compareTo(o);
    }

    @Override
    public String toString() {
        return "Mixed: " + fileUpload.toString();
    }

    public ChannelBuffer getChunk(int length) throws IOException {
        return fileUpload.getChunk(length);
    }

    public File getFile() throws IOException {
        return fileUpload.getFile();
    }

}
