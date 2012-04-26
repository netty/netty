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
package org.jboss.netty.handler.codec.http.websocketx;

import java.net.URI;
import java.util.Map;

/**
 * Instances the appropriate handshake class to use for clients
 */
public class WebSocketClientHandshakerFactory {

    /**
     * Instances a new handshaker
     * 
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server. Null if no sub-protocol support is required.
     * @param allowExtensions
     *            Allow extensions to be used in the reserved bits of the web socket frame
     * @param customHeaders
     *            Custom HTTP headers to send during the handshake
     * @throws WebSocketHandshakeException
     */
    public WebSocketClientHandshaker newHandshaker(URI webSocketURL, WebSocketVersion version, String subprotocol,
            boolean allowExtensions, Map<String, String> customHeaders) throws WebSocketHandshakeException {
        return newHandshaker(webSocketURL, version, subprotocol, allowExtensions, customHeaders, Long.MAX_VALUE);
    }
    
    /**
     * Instances a new handshaker
     * 
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server. Null if no sub-protocol support is required.
     * @param allowExtensions
     *            Allow extensions to be used in the reserved bits of the web socket frame
     * @param customHeaders
     *            Custom HTTP headers to send during the handshake
     * @param maxFramePayloadLength
     *            Maximum allowable frame payload length. Setting this value to your application's requirement may
     *            reduce denial of service attacks using long data frames.
     * @throws WebSocketHandshakeException
     */
    public WebSocketClientHandshaker newHandshaker(URI webSocketURL, WebSocketVersion version, String subprotocol,
            boolean allowExtensions, Map<String, String> customHeaders, long maxFramePayloadLength) throws WebSocketHandshakeException {
        if (version == WebSocketVersion.V13) {
            return new WebSocketClientHandshaker13(webSocketURL, version, subprotocol, allowExtensions, customHeaders, maxFramePayloadLength);
        }
        if (version == WebSocketVersion.V08) {
            return new WebSocketClientHandshaker08(webSocketURL, version, subprotocol, allowExtensions, customHeaders, maxFramePayloadLength);
        }
        if (version == WebSocketVersion.V00) {
            return new WebSocketClientHandshaker00(webSocketURL, version, subprotocol, customHeaders, maxFramePayloadLength);
        }

        throw new WebSocketHandshakeException("Protocol version " + version.toString() + " not supported.");

    }
    
}
