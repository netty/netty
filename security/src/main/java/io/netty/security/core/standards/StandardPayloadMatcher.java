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
import io.netty.security.core.InternalBufferUtils;
import io.netty.security.core.payload.BufferPayload;
import io.netty.security.core.payload.PayloadMatcher;
import io.netty.security.core.payload.RegexPayload;
import io.netty.util.CharsetUtil;

/**
 * {@link StandardPayloadMatcher} matches {@link RegexPayload} or {@link BufferPayload} needle
 * against {@link ByteBuf} haystack.
 */
public class StandardPayloadMatcher implements PayloadMatcher<Object, Object> {

    @Override
    public boolean validate(Object needle, Object haystack) {
        if (!(haystack instanceof ByteBuf)) {
            throw new IllegalArgumentException("Unsupported Haystack: " + haystack);
        }

        ByteBuf haystackBuffer = (ByteBuf) haystack;
        if (needle instanceof RegexPayload) {
            RegexPayload payload = (RegexPayload) needle;
            return payload.needle().matcher(haystackBuffer.toString(CharsetUtil.US_ASCII)).find();
        } else if (needle instanceof BufferPayload) { // This also covers HexPayload
            BufferPayload payload = (BufferPayload) needle;
            return InternalBufferUtils.bytesBefore(haystackBuffer, InternalBufferUtils.UNCHECKED_LOAD_BYTE_BUFFER,
                    payload.needle(), InternalBufferUtils.UNCHECKED_LOAD_BYTE_BUFFER) >= 0;
        } else {
            throw new IllegalArgumentException("Unsupported Needle: " + needle);
        }
    }
}
