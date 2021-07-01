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

import io.netty5.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;

final class Http2ClientStreamMuxer extends AbstractHttp2StreamMuxer {

    Http2ClientStreamMuxer(DefaultHttp2Channel channel, SensitivityDetector headerSensitivityDetector) {
        super(channel, false, headerSensitivityDetector);
    }

    Http2ClientStreamMuxer(DefaultHttp2Channel channel, SensitivityDetector headerSensitivityDetector,
                           DefaultChannelFlowControlledBytesDistributor defaultDistributor) {
        super(channel, false, headerSensitivityDetector, defaultDistributor);
    }

    @Override
    protected void initRemoteInitiatedStream(Http2StreamChannel stream) {
        stream.pipeline().addLast(new Http2ClientRequestStreamInitializer(headerSensitivityDetector,
                defaultDistributor));
    }

    @Override
    protected void initLocalInitiatedStream(Http2StreamChannel stream) {
        stream.pipeline().addLast(new Http2ClientRequestStreamInitializer(headerSensitivityDetector,
                defaultDistributor));
    }
}
