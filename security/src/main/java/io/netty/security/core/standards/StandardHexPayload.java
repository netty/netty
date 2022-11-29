/*
 * Copyright 2022 The Netty Project
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
package io.netty.security.core.standards;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.security.core.payload.HexPayload;
import io.netty.util.internal.ObjectUtil;

import static io.netty.security.core.Util.hexStringToByteArray;

/**
 * {@link StandardHexPayload} handles Hex Payload Needle. It will convert
 * Hex String or Hex {@link ByteBuf} into Hex {@link ByteBuf}.
 */
public final class StandardHexPayload implements HexPayload {

    private final ByteBuf buffer;

    private StandardHexPayload(ByteBuf buffer) {
        this.buffer = ObjectUtil.checkNotNull(buffer, "Buffer");
    }

    /**
     * Create a new {@link StandardRegexPayload} with specified {@link String}
     *
     * @param hexString {@link String} to use as needle
     * @return New {@link StandardRegexPayload} instance
     */
    public static StandardHexPayload create(String hexString) {
        byte[] hex = hexStringToByteArray(hexString);
        return new StandardHexPayload(Unpooled.wrappedBuffer(hex));
    }

    @Override
    public ByteBuf needle() {
        return buffer;
    }
}
