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

package io.netty.handler.codec.h2new;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.DuplexChannel;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2WindowUpdateFrame;
import io.netty.util.concurrent.Promise;

/**
 * A {@link DuplexChannel} which supports {@link ChannelHandler#channelRead(ChannelHandlerContext, Object)} reading} and
 * {@link ChannelHandler#write(ChannelHandlerContext, Object, Promise)}  writing} of {@link Http2Frame}s.
 * Additionally, it translates HTTP/2 protocol semantics to netty channel semantics as follows:
 * <ul>
 *     <li>{@link Http2WindowUpdateFrame} when read from the stream, updates {@link Channel#isWritable()},
 *     {@link Channel#bytesBeforeWritable()} and {@link Channel#bytesBeforeUnwritable()}.</li>
 *     <li>Sends an {@link ChannelInputShutdownEvent} on the pipeline when an {@link Http2Frame} is read with an
 *     END_STREAM flag set.</li>
 *     <li>Closes the channel after {@link Http2ResetFrame} is read</li>
 * </ul>
 */
public interface Http2StreamChannel extends Channel {
    int streamId();

    @Override
    Http2Channel parent();
}
