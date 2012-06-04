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
package io.netty.channel;

public class AbstractChannelHandler implements ChannelHandler {

    // Not using volatile because it's used only for a sanity check.
    boolean added;

    final boolean isSharable() {
        return getClass().isAnnotationPresent(Sharable.class);
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    @Override
    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    @Override
    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    @Override
    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }
}
