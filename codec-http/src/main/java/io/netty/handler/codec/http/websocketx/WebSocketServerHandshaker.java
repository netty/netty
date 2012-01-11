/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http.websocketx;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.CharsetUtil;

/**
 * Base class for server side web socket opening and closing handshakes
 */
public abstract class WebSocketServerHandshaker {

    private String webSocketURL;

    private String subProtocols;

    private String[] subProtocolsArray;

    private WebSocketVersion version = WebSocketVersion.UNKNOWN;

    /**
     * Constructor specifying the destination web socket location
     * 
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param subProtocols
     *            CSV of supported protocols. Null if sub protocols not supported.
     */
    public WebSocketServerHandshaker(String webSocketURL, String subProtocols) {
        this.webSocketURL = webSocketURL;
        this.subProtocols = subProtocols;

        if (this.subProtocols != null) {
            this.subProtocolsArray = subProtocols.split(",");
            for (int i = 0; i < this.subProtocolsArray.length; i++) {
                this.subProtocolsArray[i] = this.subProtocolsArray[i].trim();
            }
        }
    }

    /**
     * Returns the URL of the web socket
     */
    public String getWebSocketURL() {
        return webSocketURL;
    }

    public void setWebSocketURL(String webSocketURL) {
        this.webSocketURL = webSocketURL;
    }

    /**
     * Returns the CSV of supported sub protocols
     */
    public String getSubProtocols() {
        return subProtocols;
    }

    public void setSubProtocols(String subProtocols) {
        this.subProtocols = subProtocols;
    }

    /**
     * Returns the version of the specification being supported
     */
    public WebSocketVersion getVersion() {
        return version;
    }

    public void setVersion(WebSocketVersion version) {
        this.version = version;
    }

    /**
     * Performs the opening handshake
     * 
     * @param channel
     *            Channel
     * @param req
     *            HTTP Request
     * @throws NoSuchAlgorithmException
     */
    public abstract void performOpeningHandshake(Channel channel, HttpRequest req);

    /**
     * Performs the closing handshake
     * 
     * @param channel
     *            Channel
     * @param frame
     *            Closing Frame that was received
     */
    public abstract void performClosingHandshake(Channel channel, CloseWebSocketFrame frame);

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
     * SHA-1 hashing. Instance this we think it is not thread safe
     * 
     * @param bytes
     *            byte to hash
     * @return hashed
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
     * Selects the first matching supported sub protocol
     * 
     * @param requestedSubProtocol
     *            CSV of protocols to be supported. e.g. "chat, superchat"
     * @return First matching supported sub protocol. Null if not found.
     */
    protected String selectSubProtocol(String requestedSubProtocol) {
        if (requestedSubProtocol == null || this.subProtocolsArray == null) {
            return null;
        }

        String[] requesteSubProtocolsArray = requestedSubProtocol.split(",");
        for (int i = 0; i < requesteSubProtocolsArray.length; i++) {
            String requesteSubProtocol = requesteSubProtocolsArray[i].trim();

            for (String supportedSubProtocol : this.subProtocolsArray) {
                if (requesteSubProtocol.equals(supportedSubProtocol)) {
                    return requesteSubProtocol;
                }
            }
        }

        // No match found
        return null;
    }
}
