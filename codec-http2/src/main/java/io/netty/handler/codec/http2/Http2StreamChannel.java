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

package io.netty.handler.codec.http2;

import io.netty.channel.Channel;
import io.netty.util.internal.UnstableApi;

// TODO: Should we have an extra method to "open" the stream and so Channel and take care of sending the
//       Http2HeadersFrame under the hood ?
// TODO: Should we extend SocketChannel and map input and output state to the stream state ?
//
@UnstableApi
public interface Http2StreamChannel extends Channel {

    /**
     * Returns the {@link Http2FrameStream} that belongs to this channel.
     */
    Http2FrameStream stream();
}
