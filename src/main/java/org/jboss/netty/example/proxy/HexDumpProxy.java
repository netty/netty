/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.example.proxy;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class HexDumpProxy {

    static final int LOCAL_PORT = Integer.parseInt(System.getProperty("localPort", "8443"));
    static final String REMOTE_HOST = System.getProperty("remoteHost", "www.google.com");
    static final int REMOTE_PORT = Integer.parseInt(System.getProperty("remotePort", "443"));

    public static void main(String[] args) {
        System.err.println("Proxying *:" + LOCAL_PORT + " to " + REMOTE_HOST + ':' + REMOTE_PORT + " ...");

        // Configure the bootstrap.
        Executor executor = Executors.newCachedThreadPool();
        ServerBootstrap sb = new ServerBootstrap(new NioServerSocketChannelFactory(executor, executor));

        // Set up the event pipeline factory.
        ClientSocketChannelFactory cf = new NioClientSocketChannelFactory(executor, executor);

        sb.setPipelineFactory(new HexDumpProxyPipelineFactory(cf));

        // Start up the server.
        sb.bind(new InetSocketAddress(LOCAL_PORT));
    }
}
