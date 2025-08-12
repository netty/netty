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
 * The default {@link Socks5PrivateAuthResponse} implementation.
 */
public final class DefaultSocks5PrivateAuthResponse extends AbstractSocks5Message
    implements Socks5PrivateAuthResponse {

    /**
     * The authentication status.
     */
    private final Socks5PrivateAuthStatus status;

    /**
     * Creates a new instance with the specified status.
     *
     * @param authStatus the authentication status
     */
    public DefaultSocks5PrivateAuthResponse(final Socks5PrivateAuthStatus authStatus) {
        this.status = ObjectUtil.checkNotNull(authStatus, "authStatus");
    }

    @Override
    public Socks5PrivateAuthStatus status() {
        return status;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(StringUtil.simpleClassName(this));

        DecoderResult decoderResult = decoderResult();
        if (!decoderResult.isSuccess()) {
            buf.append("(decoderResult: ");
            buf.append(decoderResult);
            buf.append(", status: ");
            buf.append(status);
            buf.append(')');
        } else {
            buf.append("(status: ");
            buf.append(status);
            buf.append(')');
        }

        return buf.toString();
    }
}
