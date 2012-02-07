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
package io.netty.channel.socket.oio;

import static io.netty.channel.Channels.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;

final class OioAcceptedSocketChannel extends OioSocketChannel {

    private final PushbackInputStream in;
    private final OutputStream out;

    static OioAcceptedSocketChannel create(Channel parent,
            ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink,
            Socket socket) {
        OioAcceptedSocketChannel instance = new OioAcceptedSocketChannel(
                parent, factory, pipeline, sink, socket);
        fireChannelOpen(instance);
        fireChannelBound(instance, instance.getLocalAddress());
        return instance;
    }
    
    private OioAcceptedSocketChannel(
            Channel parent,
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink,
            Socket socket) {

        super(parent, factory, pipeline, sink, socket);

        try {
            in = new PushbackInputStream(socket.getInputStream(), 1);
        } catch (IOException e) {
            throw new ChannelException("Failed to obtain an InputStream.", e);
        }
        try {
            out = socket.getOutputStream();
        } catch (IOException e) {
            throw new ChannelException("Failed to obtain an OutputStream.", e);
        }
    }

    @Override
    PushbackInputStream getInputStream() {
        return in;
    }

    @Override
    OutputStream getOutputStream() {
        return out;
    }
}
