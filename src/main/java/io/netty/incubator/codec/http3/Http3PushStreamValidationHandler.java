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

/**
 * See <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-4.1">HTTP Message Exchanges</a>.
 */
final class Http3PushStreamValidationHandler extends Http3FrameTypeDuplexValidationHandler<Http3PushStreamFrame> {

    static final Http3PushStreamValidationHandler INSTANCE = new Http3PushStreamValidationHandler();

    private Http3PushStreamValidationHandler() {
        super(Http3PushStreamFrame.class);
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}
