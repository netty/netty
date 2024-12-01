/*
 * Copyright 2021 The Netty Project
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
package io.netty.jfr.microbench;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

@Warmup(iterations = 2)
@Measurement(iterations = 3)
@State(Scope.Benchmark)
@Fork(1)
public class JfrHandlerBenchmark extends AbstractMicrobenchmark {

    private static final ChannelHandler CONSUMING_HANDLER = new ChannelInboundHandlerAdapter() {
        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            // NOOP
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            // NOOP
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            // NOOP
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            // NOOP
        }

        @Override
        public boolean isSharable() {
            return true;
        }
    };

    private static final Exception exception = new RuntimeException("");

    @Param({
            "io.netty.jfr.JfrChannelHandler", "", "io.netty.handler.logging.LoggingHandler"
    })
    public String channelHandlerClassName;

    private ChannelPipeline pipeline;

    @Setup(Level.Iteration)
    public void setup()
            throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException,
                   InstantiationException {
        pipeline = new EmbeddedChannel().pipeline();
        if (!channelHandlerClassName.isEmpty()) {
            final Class<?> channelHandlerClass = Class.forName(channelHandlerClassName);
            Object channelHandlerInstance;
            final Constructor<?> constructor = channelHandlerClass.getConstructor();
            channelHandlerInstance = constructor.newInstance();
            pipeline.addLast((ChannelHandler) channelHandlerInstance);
        }
        pipeline.addLast(CONSUMING_HANDLER);
    }

    @TearDown
    public void tearDown() {
        pipeline.channel().close();
    }

    @Fork(jvmArgsPrepend = "")
    @Benchmark
    public void propagateEventNoJfr(Blackhole hole) {
        firePipeline(hole);
    }

    @Fork(jvmArgsPrepend = { "-XX:StartFlightRecording" })
    //=dumponexit=true,settings=profile,filename=/recordings/jfrnettybench.jfr
    @Benchmark
    public void propagateEventWithJFrAndRunningRecording(Blackhole hole) {
        firePipeline(hole);
    }

    private void firePipeline(Blackhole hole) {
        for (int i = 0; i < 100; i++) {
//            hole.consume(pipeline.read());
//            hole.consume(pipeline.bind(new LocalAddress("foo")));
//            hole.consume(pipeline.connect(new LocalAddress("bar")));
//            hole.consume(pipeline.disconnect());
//            hole.consume(pipeline.close());
//            hole.consume(pipeline.deregister());
//            hole.consume(pipeline.write("dummy"));
//            hole.consume(pipeline.flush());

            hole.consume(pipeline.fireChannelRead("dummy"));
            hole.consume(pipeline.fireChannelRegistered());
            hole.consume(pipeline.fireChannelUnregistered());
            hole.consume(pipeline.fireChannelWritabilityChanged());
            hole.consume(pipeline.fireUserEventTriggered("dummy"));
            hole.consume(pipeline.fireChannelActive());
            hole.consume(pipeline.fireChannelInactive());
            hole.consume(pipeline.fireChannelReadComplete());
            hole.consume(pipeline.fireExceptionCaught(exception));
        }
    }
}
