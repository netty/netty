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
package io.netty.channel.socket;

import java.net.InetSocketAddress;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

/**
 * A TCP/IP socket {@link Channel} which was either accepted by
 * {@link ServerSocketChannel} or created by {@link ClientSocketChannelFactory}.
 * @apiviz.landmark
 * @apiviz.composedOf io.netty.channel.socket.SocketChannelConfig
 */
public interface SocketChannel extends Channel {
    @Override
    SocketChannelConfig getConfig();
    @Override
    InetSocketAddress getLocalAddress();
    @Override
    InetSocketAddress getRemoteAddress();
    
    
    /**
     * Returns the {@link ChannelFuture} which will be notified when this
     * channels input is closed.  This method always returns the same future instance.
     */
    ChannelFuture getCloseInputFuture();
    
    
    /**
     * Closes this channels input asynchronously. Once a channel's input
     * is closed, it can not be open again.  Calling this method on a closed
     * channel or on a channel that's input is closed already has no effect.  
     * Please note that this method always returns the same future instance.
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         close input request succeeds or fails
     */
    ChannelFuture closeInput();
    
    /**
     * Returns the {@link ChannelFuture} which will be notified when this
     * channels output is closed.  This method always returns the same future instance.
     */
    ChannelFuture getCloseOutputFuture();
    
    /**
     * Closes this channels output asynchronously. Once a channel's output
     * is closed, it can not be open again.  Calling this method on a closed
     * channel or on a channel that's output is closed already has no effect.  
     * Please note that this method always returns the same future instance.
     * 
     * For a TCP socket, any previously written data will be sent followed 
     * by TCP's normal connection termination sequence. 
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         close input request succeeds or fails
     */
    ChannelFuture closeOutput();
    
    /**
     * Returns {@code true} if and only if this channels input is open.
     */
    boolean isInputOpen();
    
    /**
     * Returns {@code true} if and only if this channels output is open.
     */
    boolean isOutputOpen();

}
