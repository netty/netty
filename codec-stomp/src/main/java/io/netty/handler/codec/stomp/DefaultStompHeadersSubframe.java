/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.stomp;

import io.netty.handler.codec.DecoderResult;

/**
 * Default implementation of {@link StompHeadersSubframe}.
 */
public class DefaultStompHeadersSubframe implements StompHeadersSubframe {

    protected final StompCommand command;
    protected DecoderResult decoderResult = DecoderResult.SUCCESS;
    protected final DefaultStompHeaders headers;

    public DefaultStompHeadersSubframe(StompCommand command) {
        this(command, null);
    }

    DefaultStompHeadersSubframe(StompCommand command, DefaultStompHeaders headers) {
        if (command == null) {
            throw new NullPointerException("command");
        }

        this.command = command;
        this.headers = headers == null ? new DefaultStompHeaders() : headers;
    }

    @Override
    public StompCommand command() {
        return command;
    }

    @Override
    public StompHeaders headers() {
        return headers;
    }

    @Override
    public DecoderResult decoderResult() {
        return decoderResult;
    }

    @Override
    public void setDecoderResult(DecoderResult decoderResult) {
        this.decoderResult = decoderResult;
    }

    @Override
    public String toString() {
        return "StompFrame{" +
            "command=" + command +
            ", headers=" + headers +
            '}';
    }
}
