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
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.WritableByteChannel;

public interface JdkChannel extends Channel, WritableByteChannel {
    
    SelectionKey keyFor(Selector selector);
    
    SelectionKey register(Selector selector, int interestedOps, Object attachment) throws ClosedChannelException;
    
    boolean isRegistered();
    
    SocketAddress getRemoteSocketAddress();
    
    SocketAddress getLocalSocketAddress();
    
    boolean isConnected();
    
    boolean isSocketBound();
    
    boolean finishConnect() throws IOException;
    
    void disconnectSocket() throws IOException;
    
    void closeSocket() throws IOException;

    void bind(SocketAddress local) throws IOException;
    
    void connect(SocketAddress remote) throws IOException;

    void configureBlocking(boolean block) throws IOException;
}
