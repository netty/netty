/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.socksx.v5;

import io.netty.handler.codec.DecoderResult;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

/**
 * The default {@link Socks5PrivateAuthRequest} implementation.
 * <p>
 * For custom private authentication protocols, you should implement the {@link Socks5PrivateAuthRequest}
 * interface directly. Custom protocols should also implement their own encoder/decoder to handle the wire format.
 * </p>
 */
public final class DefaultSocks5PrivateAuthRequest extends AbstractSocks5Message
    implements Socks5PrivateAuthRequest {

    /**
     * The private authentication token.
     */
    private final byte[] privateToken;

    /**
     * Creates a new instance with the specified token.
     *
     * @param privateAuthToken the private authentication token
     */
    public DefaultSocks5PrivateAuthRequest(final byte[] privateAuthToken) {
        this.privateToken = ObjectUtil.checkNotNull(privateAuthToken, "privateToken").clone();
    }

    @Override
    public byte[] privateToken() {
        return privateToken.clone();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(StringUtil.simpleClassName(this));

        DecoderResult decoderResult = decoderResult();
        if (!decoderResult.isSuccess()) {
            buf.append("(decoderResult: ");
            buf.append(decoderResult);
            buf.append(", privateToken: ****)");
        } else {
            buf.append("(privateToken: ****)");
        }

        return buf.toString();
    }
}
