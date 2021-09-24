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

import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.Send;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import static java.util.Objects.requireNonNull;

/**
 * Disk FileUpload implementation that stores file into real files
 */
public class DiskFileUpload extends AbstractDiskHttpData<DiskFileUpload> implements FileUpload<DiskFileUpload> {
    public static String baseDirectory;

    public static boolean deleteOnExitTemporaryFile = true;

    public static final String prefix = "FUp_";

    public static final String postfix = ".tmp";

    private final String baseDir;

    private final boolean deleteOnExit;

    private String filename;

    private String contentType;

    private String contentTransferEncoding;

    public DiskFileUpload(BufferAllocator allocator, String name, String filename, String contentType,
                          String contentTransferEncoding, Charset charset, long size, String baseDir,
                          boolean deleteOnExit) {
        super(allocator, name, charset, size);
        setFilename(filename);
        setContentType(contentType);
        setContentTransferEncoding(contentTransferEncoding);
        this.baseDir = baseDir == null ? baseDirectory : baseDir;
        this.deleteOnExit = deleteOnExit;
    }

    public DiskFileUpload(BufferAllocator allocator, String name, String filename, String contentType,
                          String contentTransferEncoding, Charset charset, long size) {
        this(allocator, name, filename, contentType, contentTransferEncoding,
                charset, size, baseDirectory, deleteOnExitTemporaryFile);
    }

    @Override
    public HttpDataType getHttpDataType() {
        return HttpDataType.FileUpload;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public void setFilename(String filename) {
        requireNonNull(filename, "filename");
        this.filename = filename;
    }

    @Override
    public int hashCode() {
        return FileUploadUtil.hashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FileUpload && FileUploadUtil.equals(this, (FileUpload<?>) o);
    }

    @Override
    public int compareTo(InterfaceHttpData o) {
        if (!(o instanceof FileUpload)) {
            throw new ClassCastException("Cannot compare " + getHttpDataType() +
                    " with " + o.getHttpDataType());
        }
        return compareTo((FileUpload<?>) o);
    }

    public int compareTo(FileUpload<?> o) {
        return FileUploadUtil.compareTo(this, o);
    }

    @Override
    public void setContentType(String contentType) {
        requireNonNull(contentType, "contentType");
        this.contentType = contentType;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public String getContentTransferEncoding() {
        return contentTransferEncoding;
    }

    @Override
    public void setContentTransferEncoding(String contentTransferEncoding) {
        this.contentTransferEncoding = contentTransferEncoding;
    }

    @Override
    public String toString() {
        File file = null;
        try {
            file = getFile();
        } catch (IOException e) {
            // Should not occur.
        }

        return HttpHeaderNames.CONTENT_DISPOSITION + ": " +
               HttpHeaderValues.FORM_DATA + "; " + HttpHeaderValues.NAME + "=\"" + getName() +
                "\"; " + HttpHeaderValues.FILENAME + "=\"" + filename + "\"\r\n" +
                HttpHeaderNames.CONTENT_TYPE + ": " + contentType +
                (getCharset() != null? "; " + HttpHeaderValues.CHARSET + '=' + getCharset().name() + "\r\n" : "\r\n") +
                HttpHeaderNames.CONTENT_LENGTH + ": " + length() + "\r\n" +
                "Completed: " + isCompleted() +
                "\r\nIsInMemory: " + isInMemory() + "\r\nRealFile: " +
                (file != null ? file.getAbsolutePath() : "null") + " DeleteAfter: " + deleteOnExit;
    }

    @Override
    protected boolean deleteOnExit() {
        return deleteOnExit;
    }

    @Override
    protected String getBaseDirectory() {
        return baseDir;
    }

    @Override
    protected String getDiskFilename() {
        return "upload";
    }

    @Override
    protected String getPostfix() {
        return postfix;
    }

    @Override
    protected String getPrefix() {
        return prefix;
    }

    @Override
    public Send<DiskFileUpload> send() {
        return Send.sending(DiskFileUpload.class, () -> this);
    }

    @Override
    public DiskFileUpload touch(Object hint) {
        return this;
    }
}
