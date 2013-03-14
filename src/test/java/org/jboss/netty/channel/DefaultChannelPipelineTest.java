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
package org.jboss.netty.channel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class DefaultChannelPipelineTest {
    @Test
    public void testReplaceChannelHandler() {
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline();

        SimpleChannelHandler handler1 = new SimpleChannelHandler();
        pipeline.addLast("handler1", handler1);
        pipeline.addLast("handler2", handler1);
        pipeline.addLast("handler3", handler1);
        assertSame(pipeline.get("handler1"), handler1);
        assertSame(pipeline.get("handler2"), handler1);
        assertSame(pipeline.get("handler3"), handler1);

        SimpleChannelHandler newHandler1 = new SimpleChannelHandler();
        pipeline.replace("handler1", "handler1", newHandler1);
        assertSame(pipeline.get("handler1"), newHandler1);

        SimpleChannelHandler newHandler3 = new SimpleChannelHandler();
        pipeline.replace("handler3", "handler3", newHandler3);
        assertSame(pipeline.get("handler3"), newHandler3);

        SimpleChannelHandler newHandler2 = new SimpleChannelHandler();
        pipeline.replace("handler2", "handler2", newHandler2);
        assertSame(pipeline.get("handler2"), newHandler2);
    }

    // Test for #505
    @Test
    public void testToString() {
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline();
        assertNotNull(pipeline.toString());
    }

    @Test
    public void testLifeCycleAware() {
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline();

        List<LifeCycleAwareTestHandler> handlers = new ArrayList<LifeCycleAwareTestHandler>();

        for (int i = 0; i < 20; i++) {
            LifeCycleAwareTestHandler handler = new LifeCycleAwareTestHandler("handler-" + i);

            // Add handler.
            pipeline.addFirst(handler.name, handler);

            // Validate handler life-cycle methods called.
            handler.validate(true, true, false, false);

            // Store handler into the list.
            handlers.add(handler);
        }

        // Change the order of remove operations over all handlers in the pipeline.
        Collections.shuffle(handlers);

        for (LifeCycleAwareTestHandler handler : handlers) {
            assertSame(handler, pipeline.remove(handler.name));

            // Validate handler life-cycle methods called.
            handler.validate(true, true, true, true);
        }
    }

    /** Test handler to validate life-cycle aware behavior. */
    private static final class LifeCycleAwareTestHandler extends SimpleChannelHandler
        implements LifeCycleAwareChannelHandler {
        private final String name;

        private boolean beforeAdd;
        private boolean afterAdd;
        private boolean beforeRemove;
        private boolean afterRemove;

        /**
         * Constructs life-cycle aware test handler.
         *
         * @param name Handler name to display in assertion messages.
         */
        private LifeCycleAwareTestHandler(String name) {
            this.name = name;
        }

        public void validate(boolean beforeAdd, boolean afterAdd, boolean beforeRemove, boolean afterRemove) {
            assertEquals(name, beforeAdd, this.beforeAdd);
            assertEquals(name, afterAdd, this.afterAdd);
            assertEquals(name, beforeRemove, this.beforeRemove);
            assertEquals(name, afterRemove, this.afterRemove);
        }

        public void beforeAdd(ChannelHandlerContext ctx) {
            validate(false, false, false, false);

            beforeAdd = true;
        }

        public void afterAdd(ChannelHandlerContext ctx) {
            validate(true, false, false, false);

            afterAdd = true;
        }

        public void beforeRemove(ChannelHandlerContext ctx) {
            validate(true, true, false, false);

            beforeRemove = true;
        }

        public void afterRemove(ChannelHandlerContext ctx) {
            validate(true, true, true, false);

            afterRemove = true;
        }
    }
}
