/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.rtsp;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMessageEncoder;

/**
 * Encodes an RTSP message represented in {@link HttpMessage} into
 * a {@link ChannelBuffer}.

 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author <a href="http://amitbhayani.blogspot.com/">Amit Bhayani</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @apiviz.landmark
 */
@Sharable
public abstract class RtspMessageEncoder extends HttpMessageEncoder {

    /**
     * Creates a new instance.
     */
    protected RtspMessageEncoder() {
    }

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel,
            Object msg) throws Exception {
        // Ignore unrelated message types such as HttpChunk.
        if (!(msg instanceof HttpMessage)) {
            return msg;
        }
        return super.encode(ctx, channel, msg);
    }
}
