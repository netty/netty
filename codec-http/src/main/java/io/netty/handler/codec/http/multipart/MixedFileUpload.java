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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Mixed implementation using both in Memory and in File with a limit of size
 */
public class MixedFileUpload implements FileUpload {

    private final String baseDir;

    private final boolean deleteOnExit;

    private FileUpload fileUpload;

    private final long limitSize;

    private final long definedSize;
    private long maxSize = DefaultHttpDataFactory.MAXSIZE;

    public MixedFileUpload(String name, String filename, String contentType,
            String contentTransferEncoding, Charset charset, long size,
            long limitSize) {
        this(name, filename, contentType, contentTransferEncoding,
                charset, size, limitSize, DiskFileUpload.baseDirectory, DiskFileUpload.deleteOnExitTemporaryFile);
    }

    public MixedFileUpload(String name, String filename, String contentType,
            String contentTransferEncoding, Charset charset, long size,
            long limitSize, String baseDir, boolean deleteOnExit) {
        this.limitSize = limitSize;
        if (size > this.limitSize) {
            fileUpload = new DiskFileUpload(name, filename, contentType,
                    contentTransferEncoding, charset, size);
        } else {
            fileUpload = new MemoryFileUpload(name, filename, contentType,
                    contentTransferEncoding, charset, size);
        }
        definedSize = size;
        this.baseDir = baseDir;
        this.deleteOnExit = deleteOnExit;
    }

    @Override
    public long getMaxSize() {
        return maxSize;
    }

    @Override
    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
        fileUpload.setMaxSize(maxSize);
    }

    @Override
    public void checkSize(long newSize) throws IOException {
        if (maxSize >= 0 && newSize > maxSize) {
            throw new IOException("Size exceed allowed maximum capacity");
        }
    }

    @Override
    public void addContent(ByteBuf buffer, boolean last)
            throws IOException {
        if (fileUpload instanceof MemoryFileUpload) {
            try {
                checkSize(fileUpload.length() + buffer.readableBytes());
                if (fileUpload.length() + buffer.readableBytes() > limitSize) {
                    DiskFileUpload diskFileUpload = new DiskFileUpload(fileUpload
                            .getName(), fileUpload.getFilename(), fileUpload
                            .getContentType(), fileUpload
                            .getContentTransferEncoding(), fileUpload.getCharset(),
                            definedSize, baseDir, deleteOnExit);
                    diskFileUpload.setMaxSize(maxSize);
                    ByteBuf data = fileUpload.getByteBuf();
                    if (data != null && data.isReadable()) {
                        diskFileUpload.addContent(data.retain(), false);
                    }
                    // release old upload
                    fileUpload.release();

                    fileUpload = diskFileUpload;
                }
            } catch (IOException e) {
                buffer.release();
                throw e;
            }
        }
        fileUpload.addContent(buffer, last);
    }

    @Override
    public void delete() {
        fileUpload.delete();
    }

    @Override
    public byte[] get() throws IOException {
        return fileUpload.get();
    }

    @Override
    public ByteBuf getByteBuf() throws IOException {
        return fileUpload.getByteBuf();
    }

    @Override
    public Charset getCharset() {
        return fileUpload.getCharset();
    }

    @Override
    public String getContentType() {
        return fileUpload.getContentType();
    }

    @Override
    public String getContentTransferEncoding() {
        return fileUpload.getContentTransferEncoding();
    }

    @Override
    public String getFilename() {
        return fileUpload.getFilename();
    }

    @Override
    public String getString() throws IOException {
        return fileUpload.getString();
    }

    @Override
    public String getString(Charset encoding) throws IOException {
        return fileUpload.getString(encoding);
    }

    @Override
    public boolean isCompleted() {
        return fileUpload.isCompleted();
    }

    @Override
    public boolean isInMemory() {
        return fileUpload.isInMemory();
    }

    @Override
    public long length() {
        return fileUpload.length();
    }

    @Override
    public long definedLength() {
        return fileUpload.definedLength();
    }

    @Override
    public boolean renameTo(File dest) throws IOException {
        return fileUpload.renameTo(dest);
    }

    @Override
    public void setCharset(Charset charset) {
        fileUpload.setCharset(charset);
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
            if (fileUpload instanceof MemoryFileUpload) {
                FileUpload memoryUpload = fileUpload;
                // change to Disk
                fileUpload = new DiskFileUpload(memoryUpload
                        .getName(), memoryUpload.getFilename(), memoryUpload
                        .getContentType(), memoryUpload
                        .getContentTransferEncoding(), memoryUpload.getCharset(),
                        definedSize, baseDir, deleteOnExit);
                fileUpload.setMaxSize(maxSize);

                // release old upload
                memoryUpload.release();
            }
        }
        fileUpload.setContent(buffer);
    }

    @Override
    public void setContent(File file) throws IOException {
        checkSize(file.length());
        if (file.length() > limitSize) {
            if (fileUpload instanceof MemoryFileUpload) {
                FileUpload memoryUpload = fileUpload;

                // change to Disk
                fileUpload = new DiskFileUpload(memoryUpload
                        .getName(), memoryUpload.getFilename(), memoryUpload
                        .getContentType(), memoryUpload
                        .getContentTransferEncoding(), memoryUpload.getCharset(),
                        definedSize, baseDir, deleteOnExit);
                fileUpload.setMaxSize(maxSize);

                // release old upload
                memoryUpload.release();
            }
        }
        fileUpload.setContent(file);
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
        if (fileUpload instanceof MemoryFileUpload) {
            FileUpload memoryUpload = fileUpload;

            // change to Disk
            fileUpload = new DiskFileUpload(fileUpload
                    .getName(), fileUpload.getFilename(), fileUpload
                    .getContentType(), fileUpload
                    .getContentTransferEncoding(), fileUpload.getCharset(),
                    definedSize, baseDir, deleteOnExit);
            fileUpload.setMaxSize(maxSize);

            // release old upload
            memoryUpload.release();
        }
        fileUpload.setContent(inputStream);
    }

    @Override
    public void setContentType(String contentType) {
        fileUpload.setContentType(contentType);
    }

    @Override
    public void setContentTransferEncoding(String contentTransferEncoding) {
        fileUpload.setContentTransferEncoding(contentTransferEncoding);
    }

    @Override
    public void setFilename(String filename) {
        fileUpload.setFilename(filename);
    }

    @Override
    public HttpDataType getHttpDataType() {
        return fileUpload.getHttpDataType();
    }

    @Override
    public String getName() {
        return fileUpload.getName();
    }

    @Override
    public int hashCode() {
        return fileUpload.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return fileUpload.equals(obj);
    }

    @Override
    public int compareTo(InterfaceHttpData o) {
        return fileUpload.compareTo(o);
    }

    @Override
    public String toString() {
        return "Mixed: " + fileUpload;
    }

    @Override
    public ByteBuf getChunk(int length) throws IOException {
        return fileUpload.getChunk(length);
    }

    @Override
    public File getFile() throws IOException {
        return fileUpload.getFile();
    }

    @Override
    public FileUpload copy() {
        return fileUpload.copy();
    }

    @Override
    public FileUpload duplicate() {
        return fileUpload.duplicate();
    }

    @Override
    public FileUpload retainedDuplicate() {
        return fileUpload.retainedDuplicate();
    }

    @Override
    public FileUpload replace(ByteBuf content) {
        return fileUpload.replace(content);
    }

    @Override
    public ByteBuf content() {
        return fileUpload.content();
    }

    @Override
    public int refCnt() {
        return fileUpload.refCnt();
    }

    @Override
    public FileUpload retain() {
        fileUpload.retain();
        return this;
    }

    @Override
    public FileUpload retain(int increment) {
        fileUpload.retain(increment);
        return this;
    }

    @Override
    public FileUpload touch() {
        fileUpload.touch();
        return this;
    }

    @Override
    public FileUpload touch(Object hint) {
        fileUpload.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return fileUpload.release();
    }

    @Override
    public boolean release(int decrement) {
        return fileUpload.release(decrement);
    }
}
