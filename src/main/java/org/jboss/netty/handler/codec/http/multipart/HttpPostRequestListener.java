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


import org.jboss.netty.buffer.ChannelBuffer;

/**
 *
 * Listener interface for decoding POST requests. This listener receives events as they are processed by it's owner
 * {@link EventedHttpPostRequestDecoder} generates them.
 *
 */

public interface HttpPostRequestListener {

    /**
     *
     * Called when the parser starts to process a POST body.
     *
     */

    public void requestStarted();

    /**
     *
     * Called when all content has been parsed and sent as events.
     *
     */

    public void requestFinished();

    /**
     *
     * Called when an {@link InterfaceHttpData} is parsed.
     *
     * @param data
     */

    public void httpDataReceived( InterfaceHttpData data );

    /**
     *
     * Called when the parser starts to process a file upload. The same request can have <strong>many</strong>
     * files being uploaded.
     *
     * @param fileUpload
     */

    public void fileUploadStarted( FileUpload fileUpload );

    /**
     *
     * Called when a file upload has ended. The file upload conents are now completely available to be used.
     *
     * @param fileUpload
     */

    public void fileUploadFinished( FileUpload fileUpload );

    /**
     *
     * Called when a chunk of the uploaded file is received. This is the same buffer that will be written to
     * the {@link FileUpload} instance so, if you want to do anything with it, make sure you either copy it's
     * contents somewhere else or return the read index to it's original position after using it.
     *
     * @param buffer
     */

    public void fileUploadChunkReceived( ChannelBuffer buffer);

}
