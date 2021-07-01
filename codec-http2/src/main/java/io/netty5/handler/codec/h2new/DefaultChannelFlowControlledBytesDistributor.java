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
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http2.Http2PriorityFrame;
import io.netty5.util.collection.IntObjectHashMap;
import io.netty5.util.collection.IntObjectMap;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

/**
 * A {@link ChannelFlowControlledBytesDistributor} that intercepts HTTP/2 frames and distributes flow control credits
 * to individual streams.
 */
final class DefaultChannelFlowControlledBytesDistributor extends ChannelHandlerAdapter
        implements ChannelFlowControlledBytesDistributor {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(DefaultChannelFlowControlledBytesDistributor.class);

    private final IntObjectMap<Object> remoteAcceptors = new IntObjectHashMap<>();
    private final IntObjectMap<Object> localAcceptors = new IntObjectHashMap<>();
    private final Channel channel;

    DefaultChannelFlowControlledBytesDistributor(Channel channel) {
        this.channel = channel;
    }

    @Override
    public DistributionAcceptor replaceRemote(int streamId, DistributionAcceptor acceptor) {
        return replace(streamId, remoteAcceptors, acceptor);
    }

    @Override
    public DistributionAcceptor replaceLocal(int streamId, DistributionAcceptor acceptor) {
        return replace(streamId, localAcceptors, acceptor);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof Http2Frame)) {
            ctx.fireChannelRead(msg);
            return;
        }

        Http2Frame http2Frame = (Http2Frame) msg;
        final int streamId = http2Frame.streamId();
        boolean dispose = false;
        switch (http2Frame.frameType()) {
            case Headers:
                final Http2HeadersFrame headersFrame = (Http2HeadersFrame) http2Frame;
                onNewStream(streamId, headersFrame);
                break;
            case Priority:
                handleReprioritization((Http2PriorityFrame) http2Frame);
                break;
            case RstStream:
                dispose = true;
                break;
            case Settings:
            case WindowUpdate:
            default:
                break;
        }

        try {
            ctx.fireChannelRead(msg);
        } finally {
            if (dispose) {
                final Object maybeAcceptor = remoteAcceptors.remove(streamId);
                if (maybeAcceptor instanceof DistributionAcceptor) {
                    handleLeftOverBytesRemote(streamId, ((DistributionAcceptor) maybeAcceptor).dispose());
                }
            }
        }
    }

    void bytesRead(int streamId, int bytes) {
        logger.debug("Channel: {}, stream id: {}, bytes read: {}", channel, streamId, bytes);
    }

    void bytesWritten(int streamId, int bytes) {
        logger.debug("Channel: {}, stream id: {}, bytes written: {}", channel, streamId, bytes);
    }

    void streamOutputClosed(int streamId) {
        logger.debug("Channel: {}, stream id: {}, output closed.", channel, streamId);
        // TODO: Can get WINDOW_UPDATE post EOS, handle that.
        // https://httpwg.org/specs/rfc7540.html#WINDOW_UPDATE

        Integer leftover = handleIOClosed(streamId, remoteAcceptors);
        if (leftover != null) {
            handleLeftOverBytesRemote(streamId, leftover);
        }
    }

    void streamInputClosed(int streamId) {
        logger.debug("Channel: {}, stream id: {}, input closed.", channel, streamId);
        // TODO: Can get WINDOW_UPDATE post EOS, handle that.
        // https://httpwg.org/specs/rfc7540.html#WINDOW_UPDATE

        Integer leftover = handleIOClosed(streamId, localAcceptors);
        if (leftover != null) {
            handleLeftOverBytesLocal(streamId, leftover);
        }
    }

    private Integer handleIOClosed(int streamId, IntObjectMap<Object> acceptor) {
        final Object removed = acceptor.remove(streamId);
        if (removed == null) {
            return null;
        }
        final int leftover;
        if (removed instanceof DistributionAcceptor) {
            leftover = ((DistributionAcceptor) removed).dispose();
        } else {
            assert removed instanceof Integer;
            leftover = (int) removed;
        }
        return leftover;
    }

    void newLocallyInitiatedStream(int streamId) {
        onNewStream(streamId, null);
    }

    private void handleReprioritization(Http2PriorityFrame http2Frame) {
    }

    private void handleLeftOverBytesRemote(int streamId, int dispose) {
    }

    private void handleLeftOverBytesLocal(int streamId, int dispose) {
    }

    private void onNewStream(int streamId, Http2HeadersFrame headersFrame) {
        initAcceptorForNewStream(streamId, headersFrame, this::forNewStreamWrite, remoteAcceptors);
        initAcceptorForNewStream(streamId, headersFrame, this::forNewStreamRead, localAcceptors);
    }

    private void initAcceptorForNewStream(int streamId, Http2HeadersFrame headersFrame,
                                          HeadersToIntFunction newStreamProvider,
                                          IntObjectMap<Object> acceptrs) {
        acceptrs.compute(streamId, (id, existing) -> {
            if (existing == null) {
                return newStreamProvider.apply(headersFrame);
            }
            if (existing instanceof DistributionAcceptor) {
                DistributionAcceptor acceptor = (DistributionAcceptor) existing;
                if (acceptor instanceof EarlyAcceptor) {
                    acceptor.accumulate(newStreamProvider.apply(headersFrame));
                }
                return existing;
            }
            assert existing instanceof Integer;
            return existing;
        });
    }

    private int forNewStreamWrite(Http2HeadersFrame headersFrame) {
        // check priority, weights and distribute
        return 65_535; // https://httpwg.org/specs/rfc7540.html#InitialWindowSize
    }

    private int forNewStreamRead(Http2HeadersFrame headersFrame) {
        // check priority, weights and distribute
        return 65_535; // https://httpwg.org/specs/rfc7540.html#InitialWindowSize
    }

    private DistributionAcceptor replace(int streamId, IntObjectMap<Object> acceptors, DistributionAcceptor acceptor) {
        if (!channel.executor().inEventLoop()) {
            throw new IllegalArgumentException("Invalid caller, not on eventloop: " + channel.executor());
        }

        final Object replaced = acceptors.replace(streamId, acceptor);
        if (replaced == null) {
            // early registration
            acceptors.put(streamId, new EarlyAcceptor(acceptor));
            return null;
        }

        if (replaced instanceof Integer) {
            acceptor.accumulate((Integer) replaced);
            return null;
        }

        assert replaced instanceof DistributionAcceptor;
        final DistributionAcceptor replacedAcceptor = (DistributionAcceptor) replaced;
        acceptor.accumulate(replacedAcceptor.dispose());
        return replacedAcceptor;
    }

    private static final class EarlyAcceptor implements DistributionAcceptor {

        private final DistributionAcceptor delegate;

        private EarlyAcceptor(DistributionAcceptor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void accumulate(int accumulate) {
            delegate.accumulate(accumulate);
        }

        @Override
        public int dispose() {
            return delegate.dispose();
        }
    }

    @FunctionalInterface
    private interface HeadersToIntFunction {
        int apply(Http2HeadersFrame headersFrame);
    }
}
