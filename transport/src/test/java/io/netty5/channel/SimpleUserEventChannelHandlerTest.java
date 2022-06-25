/*
 * Copyright 2018 The Netty Project
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
package io.netty5.channel;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferHolder;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SimpleUserEventChannelHandlerTest {

    private FooEventCatcher fooEventCatcher;
    private AllEventCatcher allEventCatcher;
    private EmbeddedChannel channel;

    @BeforeEach
    public void setUp() {
        fooEventCatcher = new FooEventCatcher();
        allEventCatcher = new AllEventCatcher();
        channel = new EmbeddedChannel(fooEventCatcher, allEventCatcher);
    }

    @Test
    public void testTypeMatch() {
        try (FooEvent fooEvent = new FooEvent()) {
            channel.pipeline().fireChannelInboundEvent(fooEvent);
            assertEquals(1, fooEventCatcher.caughtEvents.size());
            assertEquals(0, allEventCatcher.caughtEvents.size());
            assertFalse(fooEvent.isAccessible());
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testTypeMismatch() {
        try (BarEvent barEvent = new BarEvent()) {
            channel.pipeline().fireChannelInboundEvent(barEvent);
            assertEquals(0, fooEventCatcher.caughtEvents.size());
            assertEquals(1, allEventCatcher.caughtEvents.size());
        }
        assertFalse(channel.finish());
    }

    static final class FooEvent extends BufferHolder<FooEvent> {
        FooEvent() {
            super(preferredAllocator().allocate(256));
        }

        FooEvent(Buffer buf) {
            super(buf);
        }

        @Override
        protected FooEvent receive(Buffer buf) {
            return new FooEvent(buf);
        }
    }

    static final class BarEvent extends BufferHolder<BarEvent> {
        BarEvent() {
            super(preferredAllocator().allocate(256));
        }

        BarEvent(Buffer buf) {
            super(buf);
        }

        @Override
        protected BarEvent receive(Buffer buf) {
            return new BarEvent(buf);
        }
    }

    static final class FooEventCatcher extends SimpleUserEventChannelHandler<FooEvent> {

        public List<FooEvent> caughtEvents;

        FooEventCatcher() {
            caughtEvents = new ArrayList<>();
        }

        @Override
        protected void eventReceived(ChannelHandlerContext ctx, FooEvent evt) {
            caughtEvents.add(evt);
        }
    }

    static final class AllEventCatcher implements ChannelHandler {

        public List<Object> caughtEvents;

        AllEventCatcher() {
            caughtEvents = new ArrayList<>();
        }

        @Override
        public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
            caughtEvents.add(evt);
        }
    }
}
