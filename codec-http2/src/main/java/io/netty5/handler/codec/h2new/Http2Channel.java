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
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.DuplexChannel;
import io.netty.handler.codec.http2.Http2WindowUpdateFrame;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

/**
 * A {@link DuplexChannel} which supports {@link ChannelHandler#channelRead(ChannelHandlerContext, Object) reading} and
 * {@link ChannelHandler#write(ChannelHandlerContext, Object, Promise)}  writing} of {@link Http2Frame}s.
 * Additionally, it translates HTTP/2 protocol semantics to netty channel semantics as follows:
 * <ul>
 *     <li>{@link Http2WindowUpdateFrame} when read from the channel, updates {@link Channel#isWritable()},
 *     {@link Channel#bytesBeforeWritable()} and {@link Channel#bytesBeforeUnwritable()}.</li>
 * </ul>
 *
 * <h2>HTTP/2 Streams</h2>
 * This class provides <code>createStream</code> methods to create HTTP/2 streams initiated by the local peer.
 * Streams initiated by the remote peer will not be automatically created, instead the corresponding {@link Http2Frame}
 * will be sent through the {@link ChannelPipeline}.
 */
public interface Http2Channel extends Channel {
    /**
     * Creates a new {@link Http2StreamChannel}. <p>
     * See {@link #newStreamBootstrap()} to use a {@link Http2StreamChannelBootstrap} for creating a
     * {@link Http2StreamChannel}.
     *
     * @param handler to add to the created {@link Http2StreamChannel}.
     * @return the {@link Future} that will be notified once the channel was opened successfully or it failed.
     */
    Future<Http2StreamChannel> createStream(ChannelHandler handler);

    /**
     * Creates a new {@link Http2StreamChannelBootstrap} instance.
     *
     * @return {@link Http2StreamChannelBootstrap}
     */
    default Http2StreamChannelBootstrap newStreamBootstrap() {
        return new DefaultHttp2StreamChannelBootstrap(this);
    }
}
