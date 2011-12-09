/*
 * Copyright 2011 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.iostream;

import io.netty.channel.*;

import java.net.SocketAddress;

/**
 * A channel to an {@link java.io.InputStream} and an
 * {@link java.io.OutputStream}.
 * 
 * @author Daniel Bimschas
 * @author Dennis Pfisterer
 */
public class IOStreamChannel extends AbstractChannel {

    IOStreamChannel(final ChannelFactory factory, final ChannelPipeline pipeline, final ChannelSink sink) {
        super(null, factory, pipeline, sink);
    }

    @Override
    public ChannelConfig getConfig() {
        return ((IOStreamChannelSink) getPipeline().getSink()).getConfig();
    }

    @Override
    public boolean isBound() {
        return ((IOStreamChannelSink) getPipeline().getSink()).isBound();
    }

    @Override
    public boolean isConnected() {
        return ((IOStreamChannelSink) getPipeline().getSink()).isConnected();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return null;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return ((IOStreamChannelSink) getPipeline().getSink()).getRemoteAddress();
    }

    @Override
    public ChannelFuture bind(final SocketAddress localAddress) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelFuture unbind() {
        throw new UnsupportedOperationException();
    }

    void doSetClosed() {
        setClosed();
    }
}
