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
package io.netty.example.http.snoop;

import io.netty.channel.Channel;
import io.netty.channel.ChannelBootstrap;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.nio.NioEventLoop;
import io.netty.handler.codec.http.CookieEncoder;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

import java.net.InetSocketAddress;
import java.net.URI;

/**
 * A simple HTTP client that prints out the content of the HTTP response to
 * {@link System#out} to test {@link HttpSnoopServer}.
 */
public class HttpSnoopClient {

    private final URI uri;

    public HttpSnoopClient(URI uri) {
        this.uri = uri;
    }

    public void run() throws Exception {
        String scheme = uri.getScheme() == null? "http" : uri.getScheme();
        String host = uri.getHost() == null? "localhost" : uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            if (scheme.equalsIgnoreCase("http")) {
                port = 80;
            } else if (scheme.equalsIgnoreCase("https")) {
                port = 443;
            }
        }

        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            System.err.println("Only HTTP(S) is supported.");
            return;
        }

        boolean ssl = scheme.equalsIgnoreCase("https");

        // Configure the client.
        ChannelBootstrap b = new ChannelBootstrap();
        try {
            b.eventLoop(new NioEventLoop())
             .channel(new NioSocketChannel())
             .initializer(new HttpSnoopClientInitializer(ssl))
             .remoteAddress(new InetSocketAddress(host, port));

            // Make the connection attempt.
            Channel ch = b.connect().sync().channel();

            // Prepare the HTTP request.
            HttpRequest request = new DefaultHttpRequest(
                    HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath());
            request.setHeader(HttpHeaders.Names.HOST, host);
            request.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
            request.setHeader(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);

            // Set some example cookies.
            CookieEncoder httpCookieEncoder = new CookieEncoder(false);
            httpCookieEncoder.addCookie("my-cookie", "foo");
            httpCookieEncoder.addCookie("another-cookie", "bar");
            request.setHeader(HttpHeaders.Names.COOKIE, httpCookieEncoder.encode());

            // Send the HTTP request.
            ch.write(request);

            // Wait for the server to close the connection.
            ch.closeFuture().sync();
        } finally {
            // Shut down executor threads to exit.
            b.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println(
                    "Usage: " + HttpSnoopClient.class.getSimpleName() +
                    " <URL>");
            return;
        }

        URI uri = new URI(args[0]);
        new HttpSnoopClient(uri).run();
    }
}
