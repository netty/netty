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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.incubator.codec.quic.QuicStreamChannel;

import static io.netty.incubator.codec.http3.Http3CodecUtils.isServerInitiatedQuicStream;
import static io.netty.incubator.codec.http3.Http3RequestStreamCodecState.NO_STATE;

/**
 * Abstract base class that users can extend to init HTTP/3 push-streams for clients. This initializer
 * will automatically add HTTP/3 codecs etc to the {@link ChannelPipeline} as well.
 */
public abstract class Http3PushStreamClientInitializer extends ChannelInitializer<QuicStreamChannel> {

    @Override
    protected final void initChannel(QuicStreamChannel ch) {
        if (isServerInitiatedQuicStream(ch)) {
            throw new IllegalArgumentException("Using client push stream initializer for server stream: " +
                    ch.streamId());
        }
        Http3CodecUtils.verifyIsUnidirectional(ch);

        Http3ConnectionHandler connectionHandler = Http3CodecUtils.getConnectionHandlerOrClose(ch.parent());
        if (connectionHandler == null) {
            // connection should have been closed
            return;
        }
        ChannelPipeline pipeline = ch.pipeline();
        Http3RequestStreamDecodeStateValidator decodeStateValidator = new Http3RequestStreamDecodeStateValidator();
        // Add the encoder and decoder in the pipeline, so we can handle Http3Frames
        pipeline.addLast(connectionHandler.newCodec(NO_STATE, decodeStateValidator));
        pipeline.addLast(decodeStateValidator);
        // Add the handler that will validate what we write and receive on this stream.
        pipeline.addLast(connectionHandler.newPushStreamValidationHandler(ch, decodeStateValidator));
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
