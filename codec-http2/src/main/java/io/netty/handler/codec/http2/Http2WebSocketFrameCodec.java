/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CorruptedWebSocketFrameException;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.Utf8FrameValidator;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

final class Http2WebSocketFrameCodec extends ChannelDuplexHandler {
    static void install(ChannelHandlerContext ctx, boolean server, int maxFramePayloadLength,
                        WebSocketExtension extension, long timeoutMillis, boolean automaticControlFrames) {
        String name = ctx.name();
        ChannelPipeline p = ctx.pipeline();
        p.addBefore(name, null, new Http2WebSocketFrameCodec());
        p.addBefore(name, null, new WebSocket13FrameEncoder(!server));
        p.addBefore(name, null, new WebSocket13FrameDecoder(WebSocketDecoderConfig.newBuilder()
                .expectMaskedFrames(server).maxFramePayloadLength(maxFramePayloadLength)
                .allowExtensions(extension != null).closeOnProtocolViolation(false).build()));
        if (extension != null) {
            p.addBefore(name, null, new DeflateReservedBitsValidator());
            p.addBefore(name, null, extension.newExtensionEncoder());
            p.addBefore(name, null, extension.newExtensionDecoder());
        }
        p.addBefore(name, null, new Utf8FrameValidator(false));
        p.addBefore(name, null, new ControlHandler(timeoutMillis, automaticControlFrames));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Http2DataFrame) {
            Http2DataFrame data = (Http2DataFrame) msg;
            boolean end = data.isEndStream();
            // The stream channel accounts for the full flow-controlled size, including padding, as it reads.
            ByteBuf bytes = data.content().retain();
            data.release();
            ctx.fireChannelRead(bytes);
            if (end) {
                ctx.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
            }
        } else if (msg instanceof Http2HeadersFrame) {
            ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR));
            ctx.fireExceptionCaught(new IllegalStateException("unexpected HEADERS on an established WebSocket"));
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg instanceof ByteBuf ? new DefaultHttp2DataFrame((ByteBuf) msg) : msg, promise);
    }

    private static final class DeflateReservedBitsValidator extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof WebSocketFrame) {
                WebSocketFrame frame = (WebSocketFrame) msg;
                // RFC 7692 reserves RSV1 only on the first frame of a compressed message.
                if (frame.rsv() != 0 && (frame.rsv() != WebSocketExtension.RSV1 ||
                        !(frame instanceof BinaryWebSocketFrame || frame instanceof TextWebSocketFrame))) {
                    frame.release();
                    throw new CorruptedWebSocketFrameException(WebSocketCloseStatus.PROTOCOL_ERROR,
                            "unexpected reserved bits for permessage-deflate");
                }
            }
            ctx.fireChannelRead(msg);
        }
    }

    private static final class ControlHandler extends ChannelDuplexHandler {
        private final long timeoutMillis;
        private final boolean automaticControlFrames;
        private boolean sentClose;
        private boolean receivedClose;
        private boolean sentEnd;
        private boolean failed;
        private ScheduledFuture<?> timeout;

        ControlHandler(long timeoutMillis, boolean automaticControlFrames) {
            this.timeoutMillis = timeoutMillis;
            this.automaticControlFrames = automaticControlFrames;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof CloseWebSocketFrame) {
                receivedClose = true;
                if (automaticControlFrames && !sentClose) {
                    writeClose(ctx, ((CloseWebSocketFrame) msg).retainedDuplicate(), ctx.newPromise());
                }
                if (sentClose) {
                    endStream(ctx, true);
                } else {
                    // A custom handler may write its response later, including after the peer's END_STREAM.
                    startCloseTimeout(ctx);
                }
            } else if (automaticControlFrames && msg instanceof PingWebSocketFrame) {
                PingWebSocketFrame ping = (PingWebSocketFrame) msg;
                if (!receivedClose && !failed) {
                    ctx.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
                }
                ping.release();
                if (!ctx.channel().config().isAutoRead()) {
                    ctx.read();
                }
                return;
            } else if (failed && msg instanceof WebSocketFrame) {
                ReferenceCountUtil.release(msg);
                return;
            }
            ctx.fireChannelRead(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            // Data already in flight may precede the peer's close frame or its final END_STREAM.
            if (sentClose && ctx.channel().isActive() && !ctx.channel().config().isAutoRead()) {
                ctx.read();
            }
            ctx.fireChannelReadComplete();
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            // A custom handler can still answer a Ping that crossed a locally initiated Close.
            if (sentClose && msg instanceof WebSocketFrame &&
                    (automaticControlFrames || !(msg instanceof PongWebSocketFrame) || receivedClose || failed)) {
                ReferenceCountUtil.release(msg);
                promise.tryFailure(new ClosedChannelException());
            } else if (msg instanceof CloseWebSocketFrame) {
                writeClose(ctx, (CloseWebSocketFrame) msg, promise);
                if (!automaticControlFrames && receivedClose) {
                    endStream(ctx, false);
                }
            } else {
                ctx.write(msg, promise);
            }
        }

        private static void abortOnFailure(ChannelHandlerContext ctx, ChannelFuture write) {
            write.addListener(future -> {
                if (!future.isSuccess()) {
                    ctx.close();
                }
            });
        }

        private void writeClose(ChannelHandlerContext ctx, CloseWebSocketFrame frame, ChannelPromise promise) {
            sentClose = true;
            startCloseTimeout(ctx);
            abortOnFailure(ctx, ctx.write(frame, promise.unvoid()));
            if (!ctx.channel().config().isAutoRead()) {
                ctx.read();
            }
        }

        private void startCloseTimeout(ChannelHandlerContext ctx) {
            if (timeout == null) {
                // Bound both flow-controlled writes and a close exchange that doesn't complete.
                timeout = ctx.executor().schedule(() -> ctx.close(), timeoutMillis, TimeUnit.MILLISECONDS);
            }
        }

        private void endStream(ChannelHandlerContext ctx, boolean flush) {
            if (!sentEnd) {
                sentEnd = true;
                Http2DataFrame end = new DefaultHttp2DataFrame(true);
                abortOnFailure(ctx, flush ? ctx.writeAndFlush(end) : ctx.write(end));
            }
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
            if (!ctx.channel().isActive() || (!automaticControlFrames && !sentClose)) {
                ctx.close(promise);
                return;
            }
            ctx.channel().closeFuture().addListener(future -> promise.trySuccess());
            if (!sentClose) {
                writeClose(ctx, new CloseWebSocketFrame(WebSocketCloseStatus.NORMAL_CLOSURE, ctx.alloc()),
                        ctx.newPromise());
            }
            ctx.flush();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt == ChannelInputShutdownEvent.INSTANCE && !receivedClose) {
                if (!failed) {
                    failed = true;
                    ctx.fireExceptionCaught(new CorruptedWebSocketFrameException(WebSocketCloseStatus.ABNORMAL_CLOSURE,
                            "HTTP/2 stream ended before a WebSocket close frame"));
                }
                // The peer can no longer finish the WebSocket close handshake. Abort pending flow-controlled writes.
                ctx.close();
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause instanceof CorruptedWebSocketFrameException) {
                failed = true;
                if (!sentClose) {
                    WebSocketCloseStatus status = ((CorruptedWebSocketFrameException) cause).closeStatus();
                    writeClose(ctx, new CloseWebSocketFrame(status, ctx.alloc()), ctx.newPromise());
                }
                endStream(ctx, true);
            } else {
                ctx.close();
            }
            ctx.fireExceptionCaught(cause);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (timeout != null) {
                timeout.cancel(false);
            }
            ctx.fireChannelInactive();
        }
    }
}
