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
package org.jboss.netty.example.redis;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.redis.Command;
import org.jboss.netty.handler.codec.redis.RedisCommandEncoder;
import org.jboss.netty.handler.codec.redis.RedisReplyDecoder;
import org.jboss.netty.handler.codec.redis.Reply;
import org.jboss.netty.handler.queue.BlockingReadHandler;
import org.jboss.netty.util.CharsetUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RedisClient {
    private static final byte[] VALUE = "value".getBytes();

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newCachedThreadPool();
        final ClientBootstrap cb = new ClientBootstrap(new NioClientSocketChannelFactory(executor, executor));
        final BlockingReadHandler<Reply> blockingReadHandler = new BlockingReadHandler<Reply>();
        cb.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("redisEncoder", new RedisCommandEncoder());
                pipeline.addLast("redisDecoder", new RedisReplyDecoder());
                pipeline.addLast("result", blockingReadHandler);
                return pipeline;
            }
        });
        ChannelFuture redis = cb.connect(new InetSocketAddress("localhost", 6379));
        redis.await().rethrowIfFailed();
        Channel channel = redis.getChannel();

        channel.write(new Command("set", "1", "value"));
        System.out.print(blockingReadHandler.read());
        channel.write(new Command("get", "1"));
        System.out.print(blockingReadHandler.read());

        int CALLS = 1000000;
        int PIPELINE = 50;
        requestResponse(blockingReadHandler, channel, CALLS);
        pipelinedIndividualRequests(blockingReadHandler, channel, CALLS * 10, PIPELINE);
        pipelinedListOfRequests(blockingReadHandler, channel, CALLS * 10, PIPELINE);

        channel.close();
        cb.releaseExternalResources();
    }

    private static void pipelinedListOfRequests(BlockingReadHandler<Reply> blockingReadHandler, Channel channel, long CALLS, int PIPELINE) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        ChannelBuffer SET_BYTES = ChannelBuffers.copiedBuffer("SET", CharsetUtil.US_ASCII);
        for (int i = 0; i < CALLS / PIPELINE; i++) {
            List<Command> list = new ArrayList<Command>();
            for (int j = 0; j < PIPELINE; j++) {
                int base = i * PIPELINE;
                list.add(new Command(SET_BYTES, String.valueOf(base + j).getBytes(), VALUE));
            }
            channel.write(list);
            for (int j = 0; j < PIPELINE; j++) {
                blockingReadHandler.read();
            }
        }
        long end = System.currentTimeMillis();
        System.out.println(CALLS * 1000 / (end - start) + " calls per second");
    }

    private static void pipelinedIndividualRequests(BlockingReadHandler<Reply> blockingReadHandler, Channel channel, long CALLS, int PIPELINE) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        ChannelBuffer SET_BYTES = ChannelBuffers.copiedBuffer("SET", CharsetUtil.US_ASCII);
        for (int i = 0; i < CALLS / PIPELINE; i++) {
            int base = i * PIPELINE;
            for (int j = 0; j < PIPELINE; j++) {
                channel.write(new Command(SET_BYTES, String.valueOf(base + j).getBytes(), VALUE));
            }
            for (int j = 0; j < PIPELINE; j++) {
                blockingReadHandler.read();
            }
        }
        long end = System.currentTimeMillis();
        System.out.println(CALLS * 1000 / (end - start) + " calls per second");
    }

    private static void requestResponse(BlockingReadHandler<Reply> blockingReadHandler, Channel channel, int CALLS) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        ChannelBuffer SET_BYTES = ChannelBuffers.copiedBuffer("SET", CharsetUtil.US_ASCII);
        for (int i = 0; i < CALLS; i++) {
            channel.write(new Command(SET_BYTES, String.valueOf(i).getBytes(), VALUE));
            blockingReadHandler.read();
        }
        long end = System.currentTimeMillis();
        System.out.println(CALLS * 1000 / (end - start) + " calls per second");
    }

    private RedisClient() {

    }
}
