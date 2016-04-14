/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;

/**
 * Combines {@link HttpMessage} and {@link LastHttpContent} into one
 * message. So it represent a <i>complete</i> http message.
 */
public interface FullHttpMessage extends HttpMessage, LastHttpContent {
    @Override
    FullHttpMessage copy();

    @Override
    FullHttpMessage duplicate();

    @Override
    FullHttpMessage retainedDuplicate();

    @Override
    FullHttpMessage replace(ByteBuf content);

    @Override
    FullHttpMessage retain(int increment);

    @Override
    FullHttpMessage retain();

    @Override
    FullHttpMessage touch();

    @Override
    FullHttpMessage touch(Object hint);
}
