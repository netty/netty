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
package io.netty.channel.nio;

import java.nio.channels.SelectableChannel;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;

public abstract class AbstractNioMessageServerChannel extends AbstractNioMessageChannel implements ServerChannel {

    private EventLoopGroup childGroup;

    protected AbstractNioMessageServerChannel(Channel parent, EventLoop eventLoop, SelectableChannel ch,
            int readInterestOp) {
        super(parent, eventLoop, ch, readInterestOp);
    }

    public void setChildGroup(EventLoopGroup childGroup) {
        this.childGroup = childGroup;
    }

    public EventLoopGroup getChildGroup() {
        return this.childGroup;
    }

}
