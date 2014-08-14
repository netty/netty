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
package io.netty.handler.codec.socksx.v5;

import io.netty.buffer.ByteBuf;

import java.util.Collections;
import java.util.List;

/**
 * An socks init request.
 *
 * @see Socks5InitResponse
 * @see Socks5InitRequestDecoder
 */
public final class Socks5InitRequest extends Socks5Request {
    private final List<Socks5AuthScheme> authSchemes;

    public Socks5InitRequest(List<Socks5AuthScheme> authSchemes) {
        super(Socks5RequestType.INIT);
        if (authSchemes == null) {
            throw new NullPointerException("authSchemes");
        }
        this.authSchemes = authSchemes;
    }

    /**
     * Returns the List<{@link Socks5AuthScheme}> of this {@link Socks5InitRequest}
     *
     * @return The List<{@link Socks5AuthScheme}> of this {@link Socks5InitRequest}
     */
    public List<Socks5AuthScheme> authSchemes() {
        return Collections.unmodifiableList(authSchemes);
    }

    @Override
    void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(protocolVersion().byteValue());
        byteBuf.writeByte(authSchemes.size());
        for (Socks5AuthScheme authScheme : authSchemes) {
            byteBuf.writeByte(authScheme.byteValue());
        }
    }
}
