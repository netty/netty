/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.redis;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelOutboundHandlerContext;
import io.netty.handler.codec.MessageToStreamEncoder;

/**
 * {@link SimpleChannelDownstreamHandler} which encodes {@link Command}'s to {@link ChannelBuffer}'s
 */
@Sharable
public class RedisEncoder extends MessageToStreamEncoder<Object> {

    @Override
    public void encode(ChannelOutboundHandlerContext<Object> ctx, Object msg, ChannelBuffer out) throws Exception {
        Object o = msg;
        if (o instanceof Command) {
            Command command = (Command) o;
            command.write(out);
        } else if (o instanceof Iterable) {
            // Useful for transactions and database select
            for (Object i : (Iterable<?>) o) {
                if (i instanceof Command) {
                    Command command = (Command) i;
                    command.write(out);
                } else {
                    break;
                }
            }
        } else {
            throw new IllegalArgumentException("unsupported message type: " + msg.getClass().getName());
        }
    }
}
