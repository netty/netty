/*
 * Copyright 2015 The Netty Project
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
package org.jboss.netty.handler.execution;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.easymock.EasyMock;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.junit.Test;

/** @noinspection WeakerAccess, unused */
public class FairExecutionHandlerTest {

    @Test
    public void testReleaseExternalResourceViaUpstreamEvent() throws Exception {
        Channel channel = EasyMock.createMock(Channel.class);
        EasyMock.expect(channel.isOpen()).andReturn(true).anyTimes();
        ChannelEvent event = EasyMock.createMock(ChannelEvent.class);
        EasyMock.expect(event.getChannel()).andReturn(channel).anyTimes();
        EasyMock.expect(event.getFuture()).andReturn(new DefaultChannelFuture(channel, false)).anyTimes();
        EasyMock.replay(channel, event);

        final CountDownLatch latch = new CountDownLatch(1);

        FairOrderedMemoryAwareThreadPoolExecutor executor = new FairOrderedMemoryAwareThreadPoolExecutor(10, 0L, 0L);
        final ExecutionHandler handler = new ExecutionHandler(executor, true, true);
        handler.handleUpstream(new TestChannelHandlerContext(channel, handler, latch), event);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testReleaseExternalResourceViaDownstreamEvent() throws Exception {
        Channel channel = EasyMock.createMock(Channel.class);
        EasyMock.expect(channel.getCloseFuture()).andReturn(new DefaultChannelFuture(channel, false));
        ChannelEvent event = EasyMock.createMock(ChannelEvent.class);
        EasyMock.expect(event.getChannel()).andReturn(channel).anyTimes();
        EasyMock.expect(event.getFuture()).andReturn(new DefaultChannelFuture(channel, false)).anyTimes();

        EasyMock.replay(channel, event);

        final CountDownLatch latch = new CountDownLatch(1);

        FairOrderedDownstreamThreadPoolExecutor executor = new FairOrderedDownstreamThreadPoolExecutor(10);
        final ExecutionHandler handler = new ExecutionHandler(executor, true, true);
        handler.handleDownstream(new TestChannelHandlerContext(channel, handler, latch), event);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /** @noinspection unused*/
    private static final class TestChannelHandlerContext implements ChannelHandlerContext {

        private final CountDownLatch latch;
        private final ExecutionHandler handler;
        private final Channel channel;

        public TestChannelHandlerContext(Channel channel, ExecutionHandler handler, CountDownLatch latch) {
            this.latch = latch;
            this.handler = handler;
            this.channel = channel;
        }

        public Channel getChannel() {
            return channel;
        }

        public ChannelPipeline getPipeline() {
            return null;
        }

        public String getName() {
            return handler.getClass().getName();
        }

        public ChannelHandler getHandler() {
            return handler;
        }

        public boolean canHandleUpstream() {
            return true;
        }

        public boolean canHandleDownstream() {
            return true;
        }

        public void sendUpstream(ChannelEvent e) {
            handler.releaseExternalResources();
            latch.countDown();
        }

        public void sendDownstream(ChannelEvent e) {
            handler.releaseExternalResources();
            latch.countDown();
        }

        public Object getAttachment() {
            return null;
        }

        public void setAttachment(Object attachment) {
        }
    }
}
