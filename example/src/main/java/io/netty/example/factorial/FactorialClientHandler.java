/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.factorial;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.math.BigInteger;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler for a client-side channel.  This handler maintains stateful
 * information which is specific to a certain channel using member variables.
 * Therefore, an instance of this handler can cover only one channel.  You have
 * to create a new handler instance whenever you create a new channel and insert
 * this handler to avoid a race condition.
 */
public class FactorialClientHandler extends SimpleChannelInboundHandler<BigInteger> {

    private static final Logger logger = Logger.getLogger(
            FactorialClientHandler.class.getName());

    private ChannelHandlerContext ctx;
    private int i = 1;
    private int receivedMessages;
    private final int count;
    final BlockingQueue<BigInteger> answer = new LinkedBlockingQueue<BigInteger>();

    public FactorialClientHandler(int count) {
        this.count = count;
    }

    public BigInteger getFactorial() {
        boolean interrupted = false;
        for (;;) {
            try {
                BigInteger factorial = answer.take();
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                return factorial;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        sendNumbers();
    }

    @Override
    public void messageReceived0(ChannelHandlerContext ctx, final BigInteger msg) {
        receivedMessages ++;
        if (receivedMessages == count) {
            // Offer the answer after closing the connection.
            ctx.channel().close().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    boolean offered = answer.offer(msg);
                    assert offered;
                }
            });
        }
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.log(
                Level.WARNING,
                "Unexpected exception from downstream.", cause);
        ctx.close();
    }

    private void sendNumbers() {
        // Do not send more than 4096 numbers.
        boolean finished = false;
        for (int i = 0; i < 4096; i++) {
            if (i <= count) {
                ctx.write(Integer.valueOf(i));
                i ++;
            } else {
                finished = true;
                break;
            }
        }

        ChannelFuture f = ctx.flush();
        if (!finished) {
            f.addListener(numberSender);
        }
    }

    private final ChannelFutureListener numberSender = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                sendNumbers();
            }
        }
    };
}
