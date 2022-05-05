/*
 * Copyright 2017 The Netty Project
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
package io.netty5.handler.codec.h2new;

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelOption;
import io.netty5.util.AttributeKey;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;

/**
 * A bootstrap to create instances of {@link Http2StreamChannel}.
 */
public interface Http2StreamChannelBootstrap {

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link Http2StreamChannel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     */
    <T> Http2StreamChannelBootstrap option(ChannelOption<T> option, T value);

    /**
     * Allow to specify an initial attribute of the newly created {@link Http2StreamChannel}.  If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    <T> Http2StreamChannelBootstrap attr(AttributeKey<T> key, T value);

    /**
     * the {@link ChannelHandler} to use for serving the requests.
     */
    Http2StreamChannelBootstrap handler(ChannelHandler handler);

    /**
     * Open a new {@link Http2StreamChannel} to use.
     * @return the {@link Future} that will be notified once the channel was opened successfully or it failed.
     */
    Future<Http2StreamChannel> create();
}
