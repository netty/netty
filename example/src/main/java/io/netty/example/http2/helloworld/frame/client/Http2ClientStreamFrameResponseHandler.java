/*
 * Copyright 2020 The Netty Project
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
package io.netty.example.http2.helloworld.frame.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2StreamFrame;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Handles HTTP/2 stream frame responses. This is a useful approach if you specifically want to check
 * the main HTTP/2 response DATA/HEADERs, but in this example it's used purely to see whether
 * our request (for a specific stream id) has had a final response (for that same stream id).
 */
public final class Http2ClientStreamFrameResponseHandler extends SimpleChannelInboundHandler<Http2StreamFrame> {

    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) throws Exception {
        System.out.println("Received HTTP/2 'stream' frame: " + msg);

        // isEndStream() is not from a common interface, so we currently must check both
        if (msg instanceof Http2DataFrame && ((Http2DataFrame) msg).isEndStream()) {
            latch.countDown();
        } else if (msg instanceof Http2HeadersFrame && ((Http2HeadersFrame) msg).isEndStream()) {
            latch.countDown();
        }
    }

    /**
     * Waits for the latch to be decremented (i.e. for an end of stream message to be received), or for
     * the latch to expire after 5 seconds.
     * @return true if a successful HTTP/2 end of stream message was received.
     */
    public boolean responseSuccessfullyCompleted() {
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            System.err.println("Latch exception: " + ie.getMessage());
            return false;
        }
    }

}
