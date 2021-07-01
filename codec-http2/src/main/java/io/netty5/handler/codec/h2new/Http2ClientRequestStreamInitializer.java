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

import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;

final class Http2ClientRequestStreamInitializer extends ChannelInitializer<Http2StreamChannel> {
    private final SensitivityDetector headerSensitivityDetector;
    private final DefaultChannelFlowControlledBytesDistributor defaultDistributor;

    Http2ClientRequestStreamInitializer(SensitivityDetector headerSensitivityDetector,
                                        DefaultChannelFlowControlledBytesDistributor defaultDistributor) {
        this.headerSensitivityDetector = headerSensitivityDetector;
        this.defaultDistributor = defaultDistributor;
    }

    @Override
    protected void initChannel(Http2StreamChannel stream) throws Exception {
        // Http2FrameEncoder currently writes out ByteBuf which DefaultHttp2StreamChannel does not understand, so
        // convert all ByteBuf to Buffer
        stream.pipeline().addLast(new EnsureBufferOutbound());
        if (headerSensitivityDetector != null) {
            stream.pipeline().addLast(new Http2FrameEncoder(headerSensitivityDetector));
        } else {
            stream.pipeline().addLast(new Http2FrameEncoder());
        }
        final Http2RequestStreamFramesValidator framesValidator = new Http2RequestStreamFramesValidator(false);
        if (defaultDistributor != null) {
            stream.pipeline().addLast(new RequestStreamFlowControlFrameInspector(stream.streamId(), false,
                    defaultDistributor, framesValidator.localState(), framesValidator.remoteState()));
        }
        stream.pipeline().addLast(framesValidator);
        stream.pipeline().addLast(new Http2StreamStateManager(framesValidator.localState(),
                framesValidator.remoteState()));
    }
}
