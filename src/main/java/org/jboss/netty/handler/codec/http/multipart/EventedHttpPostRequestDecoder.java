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
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * Generates events for listeners during the POST decoding so the client won't need to iterate over it
 * using {@link HttpPostRequestDecoder.next()}/{@link HttpPostRequestDecoder.hasNext()} methods. Useful mainly if you want to process the HTTP request in a streaming fashion.
 * Events are forwarded to all listeners attached to this class, go to {@link HttpPostRequestListener} for a list of callbacks
 * you will receive when you add a listener to this class.
 *
 */

public class EventedHttpPostRequestDecoder extends HttpPostRequestDecoder {

    private boolean requestHasStarted;

    private List<HttpPostRequestListener> listeners = new CopyOnWriteArrayList<HttpPostRequestListener>();

    public EventedHttpPostRequestDecoder(HttpRequest request) throws ErrorDataDecoderException, IncompatibleDataDecoderException {
        super(request);
    }

    public EventedHttpPostRequestDecoder(HttpDataFactory factory, HttpRequest request) throws ErrorDataDecoderException, IncompatibleDataDecoderException {
        super(factory, request);
    }

    public EventedHttpPostRequestDecoder(HttpDataFactory factory, HttpRequest request, Charset charset) throws ErrorDataDecoderException, IncompatibleDataDecoderException {
        super(factory, request, charset);
    }

    /**
     *
     * Adds a listener to this decoder.
     *
     * @param listener
     */

    public void addListener( HttpPostRequestListener listener ) {
        listeners.add(listener);
    }

    /**
     *
     * Removes the given listener from this decoder.
     *
     * @param listener
     */

    public void removeListener( HttpPostRequestListener listener ) {
        listeners.remove(listener);
    }

    /**
     * Initialized the internals from a new chunk
     * @param chunk the new received chunk
     * @throws ErrorDataDecoderException if there is a problem with the charset decoding or
     *          other errors
     */

    @Override
    public void offer(HttpChunk chunk) throws ErrorDataDecoderException {

        if ( !requestHasStarted ) {
            this.requestHasStarted = true;
            for ( HttpPostRequestListener listener : listeners ) {
                listener.requestStarted();
            }
        }

        super.offer(chunk);

        if ( chunk.isLast() ) {
            requestHasStarted = false;
            for ( HttpPostRequestListener listener : listeners ) {
                listener.requestFinished();
            }
        }

    }

    /**
     *
     * Callback to be used by subclasses when they want to receive http processing events.
     *
     * @param data
     */
    @Override
    protected void httpDataAdded( InterfaceHttpData data ) {
        for ( HttpPostRequestListener listener : listeners ) {

            if ( data instanceof FileUpload ) {
                listener.fileUploadFinished( (FileUpload) data);
            } else {
                listener.httpDataReceived(data);
            }


        }
    }

    /**
     *
     * Callback for subclasses to know that a file upload action has started.
     *
     * @param fileUpload
     */
    @Override
    protected void fileUploadStarted( FileUpload fileUpload ) {
        for ( HttpPostRequestListener listener : listeners ) {
            listener.fileUploadStarted(fileUpload);
        }
    }

    /**
     *
     * Callback for subclasses when file upload data is read.
     *
     * @param buffer
     */
    @Override
    protected void httpChunkReceived( ChannelBuffer buffer ) {
        for ( HttpPostRequestListener listener : listeners ) {
            listener.fileUploadChunkReceived(buffer);
        }
    }

}