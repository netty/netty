/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Mixed implementation using both in Memory and in File with a limit of size
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://openr66.free.fr/">Frederic Bregier</a>
 *
 */
public class MixedFileUpload implements FileUpload {
    private FileUpload fileUpload = null;

    private long limitSize = 0;

    private long definedSize = 0;

    public MixedFileUpload(String name, String filename, String contentType,
            String contentTransferEncoding, Charset charset, long size,
            long limitSize) throws NullPointerException,
            IllegalArgumentException {
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

    @Override
    public void addContent(ChannelBuffer buffer, boolean last)
            throws IOException {
        if (fileUpload instanceof MemoryFileUpload) {
            if (fileUpload.length() + buffer.readableBytes() > limitSize) {
                DiskFileUpload diskFileUpload = new DiskFileUpload(fileUpload
                        .getName(), fileUpload.getFilename(), fileUpload
                        .getContentType(), fileUpload
                        .getContentTransferEncoding(), fileUpload.getCharset(),
                        definedSize);
                if (((MemoryFileUpload) fileUpload).getChannelBuffer() != null){
                    diskFileUpload.addContent(((MemoryFileUpload) fileUpload)
                        .getChannelBuffer(), last);
                }
                fileUpload = diskFileUpload;
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
    public ChannelBuffer getChannelBuffer() throws IOException {
        return fileUpload.getChannelBuffer();
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
    public boolean renameTo(File dest) throws IOException {
        return fileUpload.renameTo(dest);
    }

    @Override
    public void setCharset(Charset charset) {
        fileUpload.setCharset(charset);
    }

    @Override
    public void setContent(ChannelBuffer buffer) throws IOException {
        if (buffer.readableBytes() > limitSize) {
            if (fileUpload instanceof MemoryFileUpload) {
                // change to Disk
                fileUpload = new DiskFileUpload(fileUpload
                        .getName(), fileUpload.getFilename(), fileUpload
                        .getContentType(), fileUpload
                        .getContentTransferEncoding(), fileUpload.getCharset(),
                        definedSize);
            }
        }
        fileUpload.setContent(buffer);
    }

    @Override
    public void setContent(File file) throws IOException {
        if (file.length() > limitSize) {
            if (fileUpload instanceof MemoryFileUpload) {
                // change to Disk
                fileUpload = new DiskFileUpload(fileUpload
                        .getName(), fileUpload.getFilename(), fileUpload
                        .getContentType(), fileUpload
                        .getContentTransferEncoding(), fileUpload.getCharset(),
                        definedSize);
            }
        }
        fileUpload.setContent(file);
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
        if (fileUpload instanceof MemoryFileUpload) {
            // change to Disk
            fileUpload = new DiskFileUpload(fileUpload
                    .getName(), fileUpload.getFilename(), fileUpload
                    .getContentType(), fileUpload
                    .getContentTransferEncoding(), fileUpload.getCharset(),
                    definedSize);
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
    public int compareTo(InterfaceHttpData o) {
        return fileUpload.compareTo(o);
    }

    @Override
    public String toString() {
        return "Mixed: " + fileUpload.toString();
    }

    @Override
    public ChannelBuffer getChunk(int length) throws IOException {
        return fileUpload.getChunk(length);
    }

    @Override
    public File getFile() throws IOException {
        return fileUpload.getFile();
    }

}
