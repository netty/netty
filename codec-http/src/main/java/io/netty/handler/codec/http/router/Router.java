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

import io.netty.channel.ChannelInboundHandler;

/**
 * Targets of routes must be ChannelInboundHandler classes or instances.
 * In case of instances, the classes of the instances must be annotated with
 * {@code io.netty.channel.ChannelHandler.Sharable}.
 */
public class Router extends DualMethodRouter<ChannelInboundHandler, Router> {
    @Override protected Router getThis() { return this; }

    @Override public String toString() { return super.toString(); }
}
