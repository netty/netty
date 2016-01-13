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
 */package io.netty.channel.embedded;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerInvoker;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel.LastInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class EmbeddedChannelPipelineTestRealChannel {

    @Rule
    public final EmbeddedChannelRule channelRule = new EmbeddedChannelRule();

    @Test(timeout = 60000)
    public void testAddLast() throws Exception {
        ChannelPipeline pipeline = channelRule.embeddedPipeline.addLast("last-handler", channelRule.mockHandler);
        assertThat("Unexpected pipeline instance returned.", pipeline, is(instanceOf(EmbeddedChannelPipeline.class)));
        assertThat("Unexpected last handler in the pipeline.", pipeline.last(),
                   is(instanceOf(LastInboundHandler.class)));
    }

    @Test(timeout = 60000)
    public void testAddLastWithGroup() throws Exception {
        ChannelPipeline pipeline = channelRule.embeddedPipeline.addLast(channelRule.group, "last-handler",
                                                                        channelRule.mockHandler);
        assertThat("Unexpected pipeline instance returned.", pipeline, is(instanceOf(EmbeddedChannelPipeline.class)));
        assertThat("Unexpected last handler in the pipeline.", pipeline.last(),
                   is(instanceOf(LastInboundHandler.class)));
    }

    @Test(timeout = 60000)
    public void testAddLastWithInvoker() throws Exception {
        ChannelPipeline pipeline = channelRule.embeddedPipeline.addLast(channelRule.invoker, "last-handler",
                                                                        channelRule.mockHandler);
        assertThat("Unexpected pipeline instance returned.", pipeline, is(instanceOf(EmbeddedChannelPipeline.class)));
        assertThat("Unexpected last handler in the pipeline.", pipeline.last(),
                   is(instanceOf(LastInboundHandler.class)));
    }

    @Test(timeout = 60000)
    public void testAddLastMulti() throws Exception {
        ChannelPipeline pipeline = channelRule.embeddedPipeline.addLast(channelRule.invoker,
                                                                        channelRule.mockHandler);
        assertThat("Unexpected pipeline instance returned.", pipeline, is(instanceOf(EmbeddedChannelPipeline.class)));
        assertThat("Unexpected last handler in the pipeline.", pipeline.last(),
                   is(instanceOf(LastInboundHandler.class)));
    }

    @Test(timeout = 60000)
    public void testAddLastMultiWithGroup() throws Exception {
        ChannelPipeline pipeline = channelRule.embeddedPipeline.addLast(channelRule.group, channelRule.mockHandler,
                                                                        channelRule.mockHandler);
        assertThat("Unexpected pipeline instance returned.", pipeline, is(instanceOf(EmbeddedChannelPipeline.class)));
        assertThat("Unexpected last handler in the pipeline.", pipeline.last(),
                   is(instanceOf(LastInboundHandler.class)));
    }

    @Test(timeout = 60000)
    public void testAddLastMultiWithInvoker() throws Exception {
        ChannelPipeline pipeline = channelRule.embeddedPipeline.addLast(channelRule.invoker,
                                                                        channelRule.mockHandler,
                                                                        channelRule.mockHandler);
        assertThat("Unexpected pipeline instance returned.", pipeline, is(instanceOf(EmbeddedChannelPipeline.class)));
        assertThat("Unexpected last handler in the pipeline.", pipeline.last(),
                   is(instanceOf(LastInboundHandler.class)));
    }

    public static class EmbeddedChannelRule extends ExternalResource {

        private EventExecutorGroup group;
        private ChannelHandler mockHandler;
        private ChannelHandlerInvoker invoker;
        private EmbeddedChannel embeddedChannel;
        private EmbeddedChannelPipeline embeddedPipeline;

        @Override
        public Statement apply(final Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    embeddedChannel = new EmbeddedChannel();
                    embeddedPipeline = (EmbeddedChannelPipeline) embeddedChannel.pipeline();
                    group = new NioEventLoopGroup();
                    invoker = embeddedChannel.eventLoop().asInvoker();
                    mockHandler = Mockito.mock(ChannelHandler.class);
                    base.evaluate();
                }
            };
        }
    }
}
