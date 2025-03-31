/*
 * Copyright 2025 The Netty Project
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

import io.netty.channel.ChannelOption;

/**
 * {@link ChannelOption}s that are specific to {@link Http2StreamChannel}s.
 *
 * @param <T>   the type of the value which is valid for the {@link ChannelOption}
 */
public final class Http2StreamChannelOption<T> extends ChannelOption<T> {
    private Http2StreamChannelOption(String name) {
        super(name);
    }

    /**
     * When set to {@code true} {@link Http2WindowUpdateFrame}s will be automatically be generated and written for
     * {@link Http2StreamChannel}s as soon as frames are passed to the user via
     * {@link io.netty.channel.ChannelPipeline#fireChannelRead(Object)}. If the user wants more control on when a
     * window update is send its possible to set it to {@code false}. In this case the user is responsible to
     * generate the correct {@link Http2WindowUpdateFrame}s and eventually write these to the channel.
     * <p>
     * See <a href="https://datatracker.ietf.org/doc/html/rfc9113#section-5.2">RFC9113 5.2. Flow Control</a> for more
     * details.
     */
    public static final ChannelOption<Boolean> AUTO_STREAM_FLOW_CONTROL =
            valueOf("AUTO_STREAM_FLOW_CONTROL");
}
