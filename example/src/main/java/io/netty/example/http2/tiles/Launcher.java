/*
 * Copyright 2015 The Netty Project
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

package io.netty.example.http2.tiles;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.nio.NioHandler;

/**
 * <p>
 * Launches both Http and Http2 servers using Netty to display a set of images
 * and simulate latency. It is a Netty version of the <a
 * href="https://http2.golang.org/gophertiles?latency=0"> Go lang HTTP2 tiles
 * demo</a>.
 * </p>
 * <p>
 * Please note that if you intent to use the JDK provider for SSL, you MUST use JDK 1.8.
 * Previous JDK versions don't have any cipher suite that is suitable for use with HTTP/2.
 * The associated ALPN library for your JDK version can be found here:
 * http://eclipse.org/jetty/documentation/current/alpn-chapter.html#alpn-versions.
 * Alternatively, you can use the OpenSsl provider. Please make sure that you run OpenSsl
 * version 1.0.2 or greater.
 * </p>
 */
public final class Launcher {

    public static void main(String[] args) {
        EventLoopGroup group = new MultithreadEventLoopGroup(NioHandler.newFactory());
        Http2Server http2 = new Http2Server(group);
        HttpServer http = new HttpServer(group);
        try {
            http2.start();
            System.err.println("Open your web browser and navigate to " + "http://" + Html.IP + ":" + HttpServer.PORT);
            http.start().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }
}
