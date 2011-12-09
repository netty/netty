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

package io.netty.channel.socket.http;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelFactory;

/**
 * A fake server socket channel factory for use in testing
 */
public class FakeServerSocketChannelFactory implements
        ServerSocketChannelFactory {

    public ChannelSink sink = new FakeChannelSink();

    public FakeServerSocketChannel createdChannel;

    @Override
    public ServerSocketChannel newChannel(ChannelPipeline pipeline) {
        createdChannel = new FakeServerSocketChannel(this, pipeline, sink);
        return createdChannel;
    }

    @Override
    public void releaseExternalResources() {
        // nothing to do
    }

}
