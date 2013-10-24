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
package org.jboss.netty.handler.codec.http.websocketx;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.net.URI;
import java.util.Map;

/**
 * Base class for web socket client handshake implementations
 */
public abstract class WebSocketClientHandshaker {

    private final URI webSocketUrl;

    private final WebSocketVersion version;

    private volatile boolean handshakeComplete;

    private final String expectedSubprotocol;

    private volatile String actualSubprotocol;

    protected final Map<String, String> customHeaders;

    private final long maxFramePayloadLength;

    /**
     * Base constructor with default values
     *
     * @param webSocketUrl
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server.
     * @param customHeaders
     *            Map of custom headers to add to the client request
     */
    protected WebSocketClientHandshaker(URI webSocketUrl, WebSocketVersion version, String subprotocol,
                                        Map<String, String> customHeaders) {
        this(webSocketUrl, version, subprotocol, customHeaders, Long.MAX_VALUE);
    }

    /**
     * Base constructor
     *
     * @param webSocketUrl
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            CSV of requested subprotocol(s) sent to the server.
     * @param customHeaders
     *            Map of custom headers to add to the client request
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     */
    protected WebSocketClientHandshaker(URI webSocketUrl, WebSocketVersion version, String subprotocol,
                                        Map<String, String> customHeaders, long maxFramePayloadLength) {
        this.webSocketUrl = webSocketUrl;
        this.version = version;
        expectedSubprotocol = subprotocol;
        this.customHeaders = customHeaders;
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    /**
     * Returns the URI to the web socket. e.g. "ws://myhost.com/path"
     */
    public URI getWebSocketUrl() {
        return webSocketUrl;
    }

    /**
     * Version of the web socket specification that is being used
     */
    public WebSocketVersion getVersion() {
        return version;
    }

    /**
     * Returns the max length for any frame's payload
     */
    public long getMaxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    /**
     * Flag to indicate if the opening handshake is complete
     */
    public boolean isHandshakeComplete() {
        return handshakeComplete;
    }

    protected void setHandshakeComplete() {
        handshakeComplete = true;
    }

    /**
     * Returns the CSV of requested subprotocol(s) sent to the server as specified in the constructor
     */
    public String getExpectedSubprotocol() {
        return expectedSubprotocol;
    }

    /**
     * Returns the subprotocol response sent by the server. Only available after end of handshake.
     * Null if no subprotocol was requested or confirmed by the server.
     */
    public String getActualSubprotocol() {
        return actualSubprotocol;
    }

    protected void setActualSubprotocol(String actualSubprotocol) {
        this.actualSubprotocol = actualSubprotocol;
    }

    /**
     * Begins the opening handshake
     *
     * @param channel
     *            Channel
     */
    public abstract ChannelFuture handshake(Channel channel) throws Exception;

    /**
     * Validates and finishes the opening handshake initiated by {@link #handshake}}.
     *
     * @param channel
     *            Channel
     * @param response
     *            HTTP response containing the closing handshake details
     */
    public abstract void finishHandshake(Channel channel, HttpResponse response);
}
