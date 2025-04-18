/*
 * Copyright 2021 The Netty Project
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
package io.netty.incubator.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.incubator.codec.quic.QuicStreamChannel;

import static io.netty.incubator.codec.http3.Http3CodecUtils.isServerInitiatedQuicStream;
import static io.netty.incubator.codec.http3.Http3CodecUtils.writeVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3RequestStreamCodecState.NO_STATE;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Abstract base class that users can extend to init HTTP/3 push-streams for servers. This initializer
 * will automatically add HTTP/3 codecs etc to the {@link ChannelPipeline} as well.
 */
public abstract class Http3PushStreamServerInitializer extends ChannelInitializer<QuicStreamChannel> {

    private final long pushId;

    protected Http3PushStreamServerInitializer(long pushId) {
        this.pushId = checkPositiveOrZero(pushId, "pushId");
    }

    @Override
    protected final void initChannel(QuicStreamChannel ch) {
        if (!isServerInitiatedQuicStream(ch)) {
            throw new IllegalArgumentException("Using server push stream initializer for client stream: " +
                    ch.streamId());
        }
        Http3CodecUtils.verifyIsUnidirectional(ch);

        // We need to write stream type into the stream before doing anything else.
        // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1
        // Just allocate 16 bytes which would be the max needed to write 2 variable length ints.
        ByteBuf buffer = ch.alloc().buffer(16);
        writeVariableLengthInteger(buffer, Http3CodecUtils.HTTP3_PUSH_STREAM_TYPE);
        writeVariableLengthInteger(buffer, pushId);
        ch.write(buffer);

        Http3ConnectionHandler connectionHandler = Http3CodecUtils.getConnectionHandlerOrClose(ch.parent());
        if (connectionHandler == null) {
            // connection should have been closed
            return;
        }

        ChannelPipeline pipeline = ch.pipeline();
        Http3RequestStreamEncodeStateValidator encodeStateValidator = new Http3RequestStreamEncodeStateValidator();
        // Add the encoder and decoder in the pipeline so we can handle Http3Frames
        pipeline.addLast(connectionHandler.newCodec(encodeStateValidator, NO_STATE));
        pipeline.addLast(encodeStateValidator);
        // Add the handler that will validate what we write and receive on this stream.
        pipeline.addLast(connectionHandler.newPushStreamValidationHandler(ch, NO_STATE));
        initPushStream(ch);
    }

    /**
     * Initialize the {@link QuicStreamChannel} to handle {@link Http3PushStreamFrame}s. At the point of calling this
     * method it is already valid to write {@link Http3PushStreamFrame}s as the codec is already in the pipeline.
     *
     * @param ch the {QuicStreamChannel} for the push stream.
     */
    protected abstract void initPushStream(QuicStreamChannel ch);
}
