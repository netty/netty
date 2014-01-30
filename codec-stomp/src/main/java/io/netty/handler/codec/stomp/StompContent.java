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

import io.netty.buffer.ByteBufHolder;

/**
 * An STOMP chunk which is used for STOMP chunked transfer-encoding.
 * {@link StompDecoder} generates {@link StompContent} after
 * {@link StompFrame} when the content is large or the encoding of the content
 * is 'chunked.  If you prefer not to receive {@link StompContent} in your handler,
 * place {@link StompAggregator} after {@link StompDecoder} in the
 * {@link io.netty.channel.ChannelPipeline}.
 */
public interface StompContent extends ByteBufHolder, StompObject {
    @Override
    StompContent copy();

    @Override
    StompContent duplicate();

    @Override
    StompContent retain();

    @Override
    StompContent retain(int increment);

    @Override
    StompContent touch();

    @Override
    StompContent touch(Object hint);

}
