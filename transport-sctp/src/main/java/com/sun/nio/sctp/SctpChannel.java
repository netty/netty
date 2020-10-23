/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.sun.nio.sctp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

@SuppressWarnings("all")
public abstract class SctpChannel extends AbstractSelectableChannel {
    static {
        UnsupportedOperatingSystemException.raise();
    }

    public static SctpChannel open() throws IOException {
        return null;
    }
    
    protected SctpChannel(SelectorProvider provider) {
        super(provider);
    }

    public abstract <T> T getOption(SctpSocketOption<T> name) throws IOException;
    public abstract <T> SctpChannel setOption(SctpSocketOption<T> name, T value) throws IOException;

    public abstract Set<SocketAddress> getAllLocalAddresses() throws IOException;
    public abstract Set<SocketAddress> getRemoteAddresses() throws IOException;

    public abstract Association association() throws IOException;
    public abstract SctpChannel bind(SocketAddress local) throws IOException;
    public abstract boolean connect(SocketAddress remote) throws IOException;
    public abstract boolean finishConnect() throws IOException;

    public abstract SctpChannel bindAddress(InetAddress inetAddress) throws IOException;
    public abstract SctpChannel unbindAddress(InetAddress inetAddress) throws IOException;

    public abstract <T> MessageInfo receive(ByteBuffer dst, T attachment, NotificationHandler<T> handler) throws IOException;
    public abstract int send(ByteBuffer src, MessageInfo messageInfo) throws IOException;
    
    public abstract Set<SctpSocketOption<?>> supportedOptions();
}
