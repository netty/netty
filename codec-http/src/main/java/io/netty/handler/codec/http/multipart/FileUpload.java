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

/**
 * FileUpload interface that could be in memory, on temporary file or any other implementations.
 *
 * Most methods are inspired from java.io.File API.
 */
public interface FileUpload extends HttpData {
    /**
     * Returns the original filename in the client's filesystem,
     * as provided by the browser (or other client software).
     * @return the original filename
     */
    String getFilename();

    /**
     * Set the original filename
     */
    void setFilename(String filename);

    /**
     * Set the Content Type passed by the browser if defined
     * @param contentType Content Type to set - must be not null
     */
    void setContentType(String contentType);

    /**
     * Returns the content type passed by the browser or null if not defined.
     * @return the content type passed by the browser or null if not defined.
     */
    String getContentType();

    /**
     * Set the Content-Transfer-Encoding type from String as 7bit, 8bit or binary
     */
    void setContentTransferEncoding(String contentTransferEncoding);

    /**
     * Returns the Content-Transfer-Encoding
     * @return the Content-Transfer-Encoding
     */
    String getContentTransferEncoding();

    @Override
    FileUpload copy();

    @Override
    FileUpload duplicate();

    @Override
    FileUpload retainedDuplicate();

    @Override
    FileUpload replace(ByteBuf content);

    @Override
    FileUpload retain();

    @Override
    FileUpload retain(int increment);

    @Override
    FileUpload touch();

    @Override
    FileUpload touch(Object hint);
}
