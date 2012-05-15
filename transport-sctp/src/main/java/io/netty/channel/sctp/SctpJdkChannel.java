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

package io.netty.channel.sctp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import com.sun.nio.sctp.SctpChannel;

import io.netty.channel.socket.nio.AbstractJdkChannel;

public class SctpJdkChannel extends AbstractJdkChannel {

    SctpJdkChannel(SctpChannel channel) {
        super(channel);
    }

    @Override
    protected SctpChannel getChannel() {
        return (SctpChannel) super.getChannel();
    }
    
    @Override
    public InetSocketAddress getRemoteSocketAddress() {
        try {
            for (SocketAddress address : getChannel().getRemoteAddresses()) {
                return (InetSocketAddress) address;
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        try {
            for (SocketAddress address : getChannel().getAllLocalAddresses()) {
                return (InetSocketAddress) address;
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    public boolean isConnected() {
        return getChannel().isOpen();
    }

    @Override
    public boolean isSocketBound() {
        try {
            return !getChannel().getAllLocalAddresses().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void disconnectSocket() throws IOException {
        closeSocket();
    }

    @Override
    public void closeSocket() throws IOException {
        for (SocketAddress address: getChannel().getAllLocalAddresses()) {
            getChannel().unbindAddress(((InetSocketAddress) address).getAddress());
        }        
    }

    @Override
    public void bind(SocketAddress local) throws IOException {
        getChannel().bind(local);
    }

    @Override
    public void connect(SocketAddress remote) throws IOException {
        getChannel().connect(remote);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean finishConnect() throws IOException {
        return getChannel().finishConnect();
    }

}
