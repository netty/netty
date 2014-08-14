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
 * @see SocksV5InitResponse
 * @see SocksV5InitRequestDecoder
 */
public final class SocksV5InitRequest extends SocksV5Request {
    private final List<SocksV5AuthScheme> authSchemes;

    public SocksV5InitRequest(List<SocksV5AuthScheme> authSchemes) {
        super(SocksV5RequestType.INIT);
        if (authSchemes == null) {
            throw new NullPointerException("authSchemes");
        }
        this.authSchemes = authSchemes;
    }

    /**
     * Returns the List<{@link SocksV5AuthScheme}> of this {@link SocksV5InitRequest}
     *
     * @return The List<{@link SocksV5AuthScheme}> of this {@link SocksV5InitRequest}
     */
    public List<SocksV5AuthScheme> authSchemes() {
        return Collections.unmodifiableList(authSchemes);
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(protocolVersion().byteValue());
        byteBuf.writeByte(authSchemes.size());
        for (SocksV5AuthScheme authScheme : authSchemes) {
            byteBuf.writeByte(authScheme.byteValue());
        }
    }
}
