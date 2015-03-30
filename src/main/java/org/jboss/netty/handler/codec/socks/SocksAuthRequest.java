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
package org.jboss.netty.handler.codec.socks;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.util.CharsetUtil;

import java.nio.charset.CharsetEncoder;

/**
 * An socks auth request.
 *
 * @see {@link SocksAuthResponse}
 * @see {@link SocksAuthRequestDecoder}
 */
public final class SocksAuthRequest extends SocksRequest {
    private static final CharsetEncoder asciiEncoder = CharsetUtil.getEncoder(CharsetUtil.US_ASCII);
    private static final SubnegotiationVersion SUBNEGOTIATION_VERSION = SubnegotiationVersion.AUTH_PASSWORD;
    private final String username;
    private final String password;

    public SocksAuthRequest(String username, String password) {
        super(SocksRequestType.AUTH);
        if (username == null) {
            throw new NullPointerException("username");
        }
        if (password == null) {
            throw new NullPointerException("password");
        }
        if (!asciiEncoder.canEncode(username) || !asciiEncoder.canEncode(password)) {
            throw new IllegalArgumentException(
                    "username: " + username + " or password: **** values should be in pure ascii");
        }
        if (username.length() > 255) {
            throw new IllegalArgumentException("username: " + username + " exceeds 255 char limit");
        }
        if (password.length() > 255) {
            throw new IllegalArgumentException("password: **** exceeds 255 char limit");
        }
        this.username = username;
        this.password = password;
    }

    /**
     * Returns username that needs to be authenticated
     *
     * @return username that needs to be authenticated
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns password that needs to be validated
     *
     * @return password that needs to be validated
     */
    public String getPassword() {
        return password;
    }

    @Override
    public void encodeAsByteBuf(ChannelBuffer channelBuffer) throws Exception {
        channelBuffer.writeByte(SUBNEGOTIATION_VERSION.getByteValue());
        channelBuffer.writeByte(username.length());
        channelBuffer.writeBytes(username.getBytes("US-ASCII"));
        channelBuffer.writeByte(password.length());
        channelBuffer.writeBytes(password.getBytes("US-ASCII"));
    }
}
