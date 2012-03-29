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
package io.netty.channel.socket.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

class NioSocketJdkChannel extends AbstractJdkChannel {


    public NioSocketJdkChannel(SocketChannel channel) {
        super(channel);
    }
    
    @Override
    protected SocketChannel getChannel() {
        return (SocketChannel) super.getChannel();
    }

    @Override
    public InetSocketAddress getRemoteSocketAddress() {
        return (InetSocketAddress) getChannel().socket().getRemoteSocketAddress();
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) getChannel().socket().getLocalSocketAddress();
    }

    @Override
    public boolean isSocketBound() {
        return getChannel().socket().isBound();
    }

    @Override
    public void bind(SocketAddress local) throws IOException {
        getChannel().socket().bind(local);
    }

    @Override
    public void connect(SocketAddress remote) throws IOException {
        getChannel().connect(remote);
    }

    @Override
    public boolean isConnected() {
        return getChannel().isConnected();
    }

    @Override
    public void disconnectSocket() throws IOException {
        getChannel().socket().close();
    }

    @Override
    public void closeSocket() throws IOException {
        getChannel().socket().close();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return getChannel().write(src);
    }
    
    @Override
    public boolean finishConnect() throws IOException {
        return getChannel().finishConnect();
    }

}
