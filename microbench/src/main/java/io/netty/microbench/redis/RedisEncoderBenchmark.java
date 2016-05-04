/*
 * Copyright 2016 The Netty Project
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
package io.netty.microbench.redis;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.redis.ArrayRedisMessage;
import io.netty.handler.codec.redis.FullBulkStringRedisMessage;
import io.netty.handler.codec.redis.RedisEncoder;
import io.netty.handler.codec.redis.RedisMessage;
import io.netty.microbench.channel.EmbeddedChannelWriteReleaseHandlerContext;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;

@State(Scope.Benchmark)
@Fork(1)
@Threads(1)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
public class RedisEncoderBenchmark extends AbstractMicrobenchmark {
    private RedisEncoder encoder;
    private ByteBuf content;
    private ChannelHandlerContext context;
    private ArrayRedisMessage redisArray;

    @Param({ "true", "false" })
    public boolean pooledAllocator;

    @Param({ "true", "false" })
    public boolean voidPromise;

    @Param({ "50", "200", "1000" })
    public int arraySize;

    @Setup(Level.Trial)
    public void setup() {
        byte[] bytes = new byte[256];
        content = Unpooled.buffer(bytes.length);
        content.writeBytes(bytes);
        ByteBuf testContent = Unpooled.unreleasableBuffer(content.asReadOnly());

        List<RedisMessage> rList = new ArrayList<RedisMessage>(arraySize);
        for (int i = 0; i < arraySize; ++i) {
            rList.add(new FullBulkStringRedisMessage(testContent));
        }
        redisArray = new ArrayRedisMessage(rList);
        encoder = new RedisEncoder();
        context = new EmbeddedChannelWriteReleaseHandlerContext(pooledAllocator ? PooledByteBufAllocator.DEFAULT :
                UnpooledByteBufAllocator.DEFAULT, encoder) {
            @Override
            protected void handleException(Throwable t) {
                handleUnexpectedException(t);
            }
        };
    }

    @TearDown(Level.Trial)
    public void teardown() {
        content.release();
        content = null;
    }

    @Benchmark
    public void writeArray() throws Exception {
        encoder.write(context, redisArray.retain(), newPromise());
    }

    private ChannelPromise newPromise() {
        return voidPromise ? context.voidPromise() : context.newPromise();
    }
}
