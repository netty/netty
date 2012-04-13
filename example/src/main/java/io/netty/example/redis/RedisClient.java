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
package io.netty.example.redis;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.Channels;
import io.netty.channel.socket.nio.NioClientSocketChannelFactory;
import io.netty.handler.codec.redis.Command;
import io.netty.handler.codec.redis.RedisDecoder;
import io.netty.handler.codec.redis.RedisEncoder;
import io.netty.handler.codec.redis.Reply;
import io.netty.handler.queue.BlockingReadHandler;

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
        final ClientBootstrap cb = new ClientBootstrap(new NioClientSocketChannelFactory(executor));
        final BlockingReadHandler<Reply> blockingReadHandler = new BlockingReadHandler<Reply>();
        cb.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("redisEncoder", new RedisEncoder());
                pipeline.addLast("redisDecoder", new RedisDecoder());
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
        byte[] SET_BYTES = "SET".getBytes();
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
        byte[] SET_BYTES = "SET".getBytes();
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
        byte[] SET_BYTES = "SET".getBytes();
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
