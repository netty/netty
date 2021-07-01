/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.h2new;

import io.netty.buffer.ByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.adaptor.ByteBufAdaptor;
import io.netty5.buffer.api.adaptor.ByteBufAllocatorAdaptor;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.adaptor.BufferConversionHandler;
import io.netty5.handler.adaptor.BufferConversionHandler.Conversion;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;

import static io.netty5.handler.adaptor.BufferConversionHandler.Conversion.BUFFER_TO_BYTEBUF;
import static io.netty5.handler.adaptor.BufferConversionHandler.Conversion.NONE;

/**
 * A {@link ChannelHandler} that converts {@link Buffer} to {@link ByteBuf} when written.
 */
final class EnsureByteBufOutbound {
    static final BufferConversionHandler ADAPTOR = new BufferConversionHandler(NONE, BUFFER_TO_BYTEBUF);

    private EnsureByteBufOutbound() {
    }
}
