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
package io.netty.handler.execution;

import static org.junit.Assert.assertTrue;

import io.netty.channel.Channel;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class ExecutionHandlerTest {

    @Test
    public void testReleaseExternalResourceViaUpstreamEvent() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        
        OrderedMemoryAwareThreadPoolExecutor executor = new OrderedMemoryAwareThreadPoolExecutor(10, 0L, 0L);
        final ExecutionHandler handler = new ExecutionHandler(executor, true, true);
        handler.handleUpstream(new TestChannelHandlerContext(handler, latch), new DummyChannelEvent());

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
    
    @Test
    public void testReleaseExternalResourceViaDownstreamEvent() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        
        OrderedMemoryAwareThreadPoolExecutor executor = new OrderedMemoryAwareThreadPoolExecutor(10, 0L, 0L);
        final ExecutionHandler handler = new ExecutionHandler(executor, true, true);
        handler.handleDownstream(new TestChannelHandlerContext(handler, latch), new DummyChannelEvent());

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
    
    private static final class DummyChannelEvent implements ChannelEvent {

        @Override
        public Channel getChannel() {
            return null;
        }

        @Override
        public ChannelFuture getFuture() {
            return null;
        }
        
    }
    
    private static final class TestChannelHandlerContext implements ChannelHandlerContext {

        private final CountDownLatch latch;
        private final ExecutionHandler handler;

        public TestChannelHandlerContext(ExecutionHandler handler, CountDownLatch latch) {
            this.latch = latch;
            this.handler = handler;
        }
        
        
        @Override
        public Channel getChannel() {
            return null;
        }

        @Override
        public ChannelPipeline getPipeline() {
            return null;
        }

        @Override
        public String getName() {
            return handler.getClass().getName();
        }

        @Override
        public ChannelHandler getHandler() {
            return handler;
        }

        @Override
        public boolean canHandleUpstream() {
            return true;
        }

        @Override
        public boolean canHandleDownstream() {
            return true;
        }

        @Override
        public void sendUpstream(ChannelEvent e) {
            try {
                handler.releaseExternalResources();
            } catch (IllegalStateException ex) {
                latch.countDown();
            }
        }

        @Override
        public void sendDownstream(ChannelEvent e) {
            try {
                handler.releaseExternalResources();
            } catch (IllegalStateException ex) {
                latch.countDown();
            }
        }

        @Override
        public Object getAttachment() {
            return null;
        }

        @Override
        public void setAttachment(Object attachment) {
            
        }
        
    }
}
