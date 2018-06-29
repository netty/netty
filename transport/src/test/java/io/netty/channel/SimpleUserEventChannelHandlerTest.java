/*
 * Copyright 2018 The Netty Project
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
package io.netty.channel;

import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SimpleUserEventChannelHandlerTest {

    private FooEventCatcher fooEventCatcher;
    private AllEventCatcher allEventCatcher;
    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        fooEventCatcher = new FooEventCatcher();
        allEventCatcher = new AllEventCatcher();
        channel = new EmbeddedChannel(fooEventCatcher, allEventCatcher);
    }

    @Test
    public void testTypeMatch() {
        FooEvent fooEvent = new FooEvent();
        channel.pipeline().fireUserEventTriggered(fooEvent);
        assertEquals(1, fooEventCatcher.caughtEvents.size());
        assertEquals(0, allEventCatcher.caughtEvents.size());
        assertEquals(0, fooEvent.refCnt());
        assertFalse(channel.finish());
    }

    @Test
    public void testTypeMismatch() {
        BarEvent barEvent = new BarEvent();
        channel.pipeline().fireUserEventTriggered(barEvent);
        assertEquals(0, fooEventCatcher.caughtEvents.size());
        assertEquals(1, allEventCatcher.caughtEvents.size());
        assertTrue(barEvent.release());
        assertFalse(channel.finish());
    }

    static final class FooEvent extends DefaultByteBufHolder {
        FooEvent() {
            super(Unpooled.buffer());
        }
    }

    static final class BarEvent extends DefaultByteBufHolder {
        BarEvent() {
            super(Unpooled.buffer());
        }
    }

    static final class FooEventCatcher extends SimpleUserEventChannelHandler<FooEvent> {

        public List<FooEvent> caughtEvents;

        FooEventCatcher() {
            caughtEvents = new ArrayList<FooEvent>();
        }

        @Override
        protected void eventReceived(ChannelHandlerContext ctx, FooEvent evt) {
            caughtEvents.add(evt);
        }
    }

    static final class AllEventCatcher extends ChannelInboundHandlerAdapter {

        public List<Object> caughtEvents;

        AllEventCatcher() {
            caughtEvents = new ArrayList<Object>();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            caughtEvents.add(evt);
        }
    }
}
