/*
 * Copyright 2010 Red Hat, Inc.
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
package org.jboss.netty.handler.codec.http.websocketx;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.base64.Base64;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.util.CharsetUtil;

/**
 * Base class for web socket client handshake implementations
 * 
 * @author <a href="http://www.veebsbraindump.com/">Vibul Imtarnasan</a>
 */
public abstract class WebSocketClientHandshaker {

    private URI webSocketURL;

    private WebSocketSpecificationVersion version = WebSocketSpecificationVersion.UNKNOWN;

    private boolean openingHandshakeCompleted = false;

    private String subProtocolRequest = null;

    private String subProtocolResponse = null;

    /**
     * 
     * @param webSocketURL
     * @param version
     * @param subProtocol
     */
    public WebSocketClientHandshaker(URI webSocketURL, WebSocketSpecificationVersion version, String subProtocol) {
        this.webSocketURL = webSocketURL;
        this.version = version;
        this.subProtocolRequest = subProtocol;
    }

    /**
     * Returns the URI to the web socket. e.g. "ws://myhost.com/path"
     */
    public URI getWebSocketURL() {
        return webSocketURL;
    }

    protected void setWebSocketURL(URI webSocketURL) {
        this.webSocketURL = webSocketURL;
    }

    /**
     * Version of the web socket specification that is being used
     */
    public WebSocketSpecificationVersion getVersion() {
        return version;
    }

    protected void setVersion(WebSocketSpecificationVersion version) {
        this.version = version;
    }

    /**
     * Flag to indicate if the opening handshake is complete
     */
    public boolean isOpeningHandshakeCompleted() {
        return openingHandshakeCompleted;
    }

    protected void setOpenningHandshakeCompleted(boolean openningHandshakeCompleted) {
        this.openingHandshakeCompleted = openningHandshakeCompleted;
    }

    /**
     * Returns the sub protocol request sent to the server as specified in the
     * constructor
     */
    public String getSubProtocolRequest() {
        return subProtocolRequest;
    }

    protected void setSubProtocolRequest(String subProtocolRequest) {
        this.subProtocolRequest = subProtocolRequest;
    }

    /**
     * Returns the sub protocol response and sent by the server. Only available
     * after end of handshake.
     */
    public String getSubProtocolResponse() {
        return subProtocolResponse;
    }

    protected void setSubProtocolResponse(String subProtocolResponse) {
        this.subProtocolResponse = subProtocolResponse;
    }

    /**
     * Performs the opening handshake
     * 
     * @param ctx
     *            Channel context
     * @param channel
     *            Channel
     */
    public abstract void beginOpeningHandshake(ChannelHandlerContext ctx, Channel channel);

    /**
     * Performs the closing handshake
     * 
     * @param ctx
     *            Channel context
     * @param response
     *            HTTP response containing the closing handshake details
     */
    public abstract void endOpeningHandshake(ChannelHandlerContext ctx, HttpResponse response) throws WebSocketHandshakeException;

    /**
     * Performs an MD5 hash
     * 
     * @param bytes
     *            Data to hash
     * @return Hashed data
     */
    protected byte[] md5(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new InternalError("MD5 not supported on this platform");
        }
    }

    /**
     * Performs an SHA-1 hash
     * 
     * @param bytes
     *            Data to hash
     * @return Hashed data
     */
    protected byte[] sha1(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            return md.digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new InternalError("SHA-1 not supported on this platform");
        }
    }

    /**
     * Base 64 encoding
     * 
     * @param bytes
     *            Bytes to encode
     * @return encoded string
     */
    protected String base64Encode(byte[] bytes) {
        ChannelBuffer hashed = ChannelBuffers.wrappedBuffer(bytes);
        return Base64.encode(hashed).toString(CharsetUtil.UTF_8);
    }

    /**
     * Creates some random bytes
     * 
     * @param size
     *            Number of random bytes to create
     * @return random bytes
     */
    protected byte[] createRandomBytes(int size) {
        byte[] bytes = new byte[size];

        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) createRandomNumber(0, 255);
        }

        return bytes;
    }

    /**
     * Generates a random number
     * 
     * @param min
     *            Minimum value
     * @param max
     *            Maximum value
     * @return Random number
     */
    protected int createRandomNumber(int min, int max) {
        int rand = (int) (Math.random() * max + min);
        return rand;
    }
}
