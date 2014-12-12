/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http.router;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.EventExecutorGroup;

/**
 * Inbound handler that converts HttpRequest to Routed and passes Routed to the
 * matched handler.
 */
public class RouterHandler extends DualAbstractRouterHandler<ChannelInboundHandler, Router> {
    public static final String ROUTER_HANDLER_NAME = RouterHandler.class.getName() + "_ROUTER_HANDLER";
    public static final String ROUTED_HANDLER_NAME = RouterHandler.class.getName() + "_ROUTED_HANDLER";

    //--------------------------------------------------------------------------

    protected EventExecutorGroup group;

    public RouterHandler(Router router) {
        super(router);
    }

    public RouterHandler group(EventExecutorGroup group) {
        this.group = group;
        return this;
    }

    public EventExecutorGroup group() {
        return group;
    }

    /**
     * Should be used to add the router to pipeline:
     * channel.pipeline().addLast(handler.name(), handler)
     */
    public String name() {
        return ROUTER_HANDLER_NAME;
    }

    @Override
    protected void routed(ChannelHandlerContext ctx, Routed routed) throws Exception {
        ChannelInboundHandler handler = (ChannelInboundHandler) routed.instanceFromTarget();

        // The handler may have been added (keep alive)
        ChannelPipeline pipeline     = ctx.pipeline();
        ChannelHandler  addedHandler = pipeline.get(ROUTED_HANDLER_NAME);
        if (handler != addedHandler) {
            if (addedHandler == null) {
                if (group == null) {
                    pipeline.addAfter(ROUTER_HANDLER_NAME, ROUTED_HANDLER_NAME, handler);
                } else {
                    pipeline.addAfter(group, ROUTER_HANDLER_NAME, ROUTED_HANDLER_NAME, handler);
                }
            } else {
                pipeline.replace(addedHandler, ROUTED_HANDLER_NAME, handler);
            }
        }

        // Pass to the routed handler
        routed.retain();
        ctx.fireChannelRead(routed);
    }
}
