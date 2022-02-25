/*
 * Copyright 2012 The Netty Project
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
package io.netty5.handler.codec.http;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Send;
import io.netty5.util.internal.StringUtil;

import static java.util.Objects.requireNonNull;

/**
 * The default {@link HttpContent} implementation.
 */
public class DefaultHttpContent extends DefaultHttpObject implements HttpContent<DefaultHttpContent> {

    private final Buffer payload;

    /**
     * Creates a new instance with the specified chunk content.
     */
    public DefaultHttpContent(Buffer payload) {
        this.payload = requireNonNull(payload, "payload");
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) +
               "(data: " + payload() + ", decoderResult: " + decoderResult() + ')';
    }

    @Override
    public Send<DefaultHttpContent> send() {
        return payload.send().map(DefaultHttpContent.class, DefaultHttpContent::new);
    }

    @Override
    public void close() {
        payload.close();
    }

    @Override
    public boolean isAccessible() {
        return payload.isAccessible();
    }

    @Override
    public DefaultHttpContent touch(Object hint) {
        payload.touch(hint);
        return this;
    }

    @Override
    public Buffer payload() {
        return payload;
    }
}
