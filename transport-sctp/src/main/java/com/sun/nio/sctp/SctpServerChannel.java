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
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

@SuppressWarnings("all")
public abstract class SctpServerChannel extends AbstractSelectableChannel {
    static {
        UnsupportedOperatingSystemException.raise();
    }

    public static SctpServerChannel open() throws IOException {
        return null;
    }

    protected SctpServerChannel(SelectorProvider provider) {
        super(provider);
    }

    public abstract <T> T getOption(SctpSocketOption<T> name) throws IOException;
    public abstract <T> SctpServerChannel setOption(SctpSocketOption<T> name, T value) throws IOException;

    public abstract Set<SocketAddress> getAllLocalAddresses() throws IOException;

    public abstract SctpServerChannel bind(SocketAddress local) throws IOException;
    public abstract SctpServerChannel bind(SocketAddress local, int backlog) throws IOException;

    public abstract SctpServerChannel bindAddress(InetAddress inetAddress) throws IOException;
    public abstract SctpServerChannel unbindAddress(InetAddress inetAddress) throws IOException;

    public abstract SctpChannel accept() throws IOException;
}
