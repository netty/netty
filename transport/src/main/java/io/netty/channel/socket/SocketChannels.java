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

import io.netty.channel.ChannelDownstreamHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelState;
import io.netty.channel.ChannelUpstreamHandler;
import io.netty.channel.Channels;
import io.netty.channel.DownstreamChannelStateEvent;
import io.netty.channel.UpstreamChannelStateEvent;

/**
 * A helper class which provides various convenience methods related with
 * {@link SocketChannel}, {@link ChannelHandler}, and {@link ChannelPipeline}.
 *
 */
public class SocketChannels extends Channels {


    /**
     * Sends a {@code "closeInput"} request to the last
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link SocketChannel}.
     *
     * @param channel  the channel to close
     *
     * @return the {@link ChannelFuture} which will be notified on closure
     */
    public static ChannelFuture closeInput(SocketChannel channel) {
        ChannelFuture future = channel.getCloseInputFuture();
        channel.getPipeline().sendDownstream(new DownstreamChannelStateEvent(
                channel, future, ChannelState.OPEN_INPUT, Boolean.FALSE));
        return future;
    }

    /**
     * Sends a {@code "closeOutput"} request to the last
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link SocketChannel}.
     *
     * @param channel  the channel to close
     *
     * @return the {@link ChannelFuture} which will be notified on closure
     */
    public static ChannelFuture closeOutput(SocketChannel channel) {
        ChannelFuture future = channel.getCloseOutputFuture();
        channel.getPipeline().sendDownstream(new DownstreamChannelStateEvent(
                channel, future, ChannelState.OPEN_OUTPUT, Boolean.FALSE));
        return future;
    }
    
    /**
     * Sends a {@code "channelInputClosed"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link SocketChannel}.
     */
    public static void fireChannelInputClosed(SocketChannel channel) {
        channel.getPipeline().sendUpstream(
                new UpstreamChannelStateEvent(
                        channel, ChannelState.OPEN_INPUT, Boolean.FALSE));
    }
    
    
    /**
     * Sends a {@code "channelInputClosed"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link SocketChannel} once the io-thread runs again.
     */
    public static ChannelFuture fireChannelInputClosedLater(final SocketChannel channel) {
        return channel.getPipeline().execute(new Runnable() {
            
            @Override
            public void run() {
                fireChannelInputClosed(channel);
            }
        });
      
    }
    
    /**
     * Sends a {@code "channelOutputClosed"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link SocketChannel}.
     */
    public static void fireChannelOutputClosed(SocketChannel channel) {
        channel.getPipeline().sendUpstream(
                new UpstreamChannelStateEvent(
                        channel, ChannelState.OPEN_OUTPUT, Boolean.FALSE));
    }
    
    
    /**
     * Sends a {@code "channelOutputClosed"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link SocketChannel} once the io-thread runs again.
     */
    public static ChannelFuture fireChannelOutputClosedLater(final SocketChannel channel) {
        return channel.getPipeline().execute(new Runnable() {
            
            @Override
            public void run() {
                fireChannelOutputClosed(channel);
            }
        });
      
    }
    
    protected SocketChannels() {
        
    }
}
