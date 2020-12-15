/*
 * Copyright 2020 The Netty Project
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
import io.netty.util.internal.StringUtil;

/**
 * Abstract base class that users can extend to init HTTP/3 request-streams. This initializer
 * will automatically add HTTP/3 codecs etc to the {@link ChannelPipeline} as well.
 */
public abstract class Http3RequestStreamInitializer extends ChannelInitializer<QuicStreamChannel> {

    @Override
    protected final void initChannel(QuicStreamChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        Http3ConnectionHandler connectionHandler = ch.parent().pipeline().get(Http3ConnectionHandler.class);
        if (connectionHandler == null) {
            throw new IllegalStateException("Couldn't obtain the " +
                    StringUtil.simpleClassName(Http3ConnectionHandler.class) + " of the parent Channel");
        }
        // Add the encoder and decoder in the pipeline so we can handle Http3Frames
        pipeline.addLast(connectionHandler.newCodec());
        // Add the handler that will validate what we write and receive on this stream.
        pipeline.addLast(Http3RequestStreamValidationHandler.newClientValidator(connectionHandler::isGoAwayReceived));
        initRequestStream(ch);
    }

    /**
     * Init the {@link QuicStreamChannel} to handle {@link Http3RequestStreamFrame}s. At the point of calling this
     * method it is already valid to write {@link Http3RequestStreamFrame}s as the codec is already in the pipeline.
     * @param ch    the {QuicStreamChannel} for the request stream.
     */
    protected abstract void initRequestStream(QuicStreamChannel ch);
}
