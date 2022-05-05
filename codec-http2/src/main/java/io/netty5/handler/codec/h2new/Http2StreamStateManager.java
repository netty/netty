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

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.socket.ChannelInputShutdownReadComplete;
import io.netty5.channel.socket.ChannelOutputShutdownEvent;
import io.netty5.util.concurrent.Future;

/**
 * A handler that maps <a href="https://httpwg.org/specs/rfc7540.html#StreamStates">HTTP/2 stream states</a> to netty
 * channel events as follows:
 * <table border="1" cellspacing="0" cellpadding="6">
 *     <th>HTTP/2 Stream state</th>
 *     <th>Netty Channel events</th>
 *     <tr>
 *         <td>IDLE</td>
 *         <td>At channel creation</td>
 *     </tr>
 *     <tr>
 *         <td>RESERVED</td>
 *         <td>Not reflected</td>
 *     </tr>
 *     <tr>
 *         <td>OPEN</td>
 *         <td>Implicitly inferred based on send/receive of {@link Http2HeadersFrame}</td>
 *     </tr>
 *     <tr>
 *         <td>Remote Closed</td>
 *         <td>{@link ChannelInputShutdownReadComplete#INSTANCE} event
 *         {@link #userEventTriggered(ChannelHandlerContext, Object) sent on the channel}.</td>
 *     </tr>
 *     <tr>
 *         <td>Local Closed</td>
 *         <td>{@link ChannelOutputShutdownEvent#INSTANCE} event
 *         {@link #userEventTriggered(ChannelHandlerContext, Object) sent on the channel}.</td>
 *     </tr>
 *     <tr>
 *         <td>Closed</td>
 *         <td>Channel {@link Channel#close() closed}.</td>
 *     </tr>
 * </table>
 */
final class Http2StreamStateManager extends ChannelHandlerAdapter {
    /**
     * <a href="https://httpwg.org/specs/rfc7540.html#StreamStates">States</a> of an HTTP/2 stream.
     */
    enum StreamState {
        Idle,
        Open,
        RemoteClosed,
        LocalClosed,
        Closed
    }
    private final Http2RequestStreamCodecState localState;
    private final Http2RequestStreamCodecState remoteState;

    private StreamState state = StreamState.Idle;

    Http2StreamStateManager(Http2RequestStreamCodecState localState, Http2RequestStreamCodecState remoteState) {
        this.localState = localState;
        this.remoteState = remoteState;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // If we already reached the terminal state (Closed) or terminal state for reads (RemoteClosed), just pass
        // through the message. We can be here either due to re-entry or for frames like PRIORITY which may arrive
        // after closure.
        if (!(msg instanceof Http2Frame) || state == StreamState.Closed || state == StreamState.RemoteClosed) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (msg instanceof Http2ResetStreamFrame) {
            state = StreamState.Closed;
            ctx.fireChannelRead(msg);
            ctx.channel().close();
            return;
        }

        if (remoteState.terminated()) {
            final StreamState oldState = state;
            state = StreamState.RemoteClosed;
            ctx.fireChannelRead(msg);
            ctx.channel().pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
            if (oldState == StreamState.LocalClosed) {
                state = StreamState.Closed;
                ctx.channel().close();
            } else {
                state = StreamState.RemoteClosed;
            }
            return;
        }

        if (remoteState.started()) {
            state = StreamState.Open;
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        final Future<Void> future = ctx.write(msg);
        if (state == StreamState.Closed || state == StreamState.LocalClosed) {
            return future;
        }

        // This handler is added before the validator in the pipeline for outbound operations, so we query state
        // after write is propagated through the pipeline.
        if (localState.terminated()) {
            final StreamState oldState = state;
            state = StreamState.LocalClosed;
            if (future.isDone()) {
                if (future.isSuccess()) {
                    ctx.channel().pipeline().fireUserEventTriggered(ChannelOutputShutdownEvent.INSTANCE);
                }
            } else {
                future.addListener(f -> {
                    if (f.isSuccess()) {
                        ctx.channel().pipeline().fireUserEventTriggered(ChannelOutputShutdownEvent.INSTANCE);
                    }
                });
            }
            if (oldState == StreamState.RemoteClosed) {
                state = StreamState.Closed;
                future.addListener(ctx.channel(), ChannelFutureListeners.CLOSE);
            }
        }
        return future;
    }
}
