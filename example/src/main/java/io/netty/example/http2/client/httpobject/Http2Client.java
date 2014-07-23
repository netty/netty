/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.example.http2.client.httpobject;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2OrHttpChooser.SelectedProtocol;
import io.netty.handler.codec.http2.Http2EventListener;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpVersion.*;

/**
 * Provides a client that waits (and provides asynchronous notification) for the connection and Http2Settings object to
 * be read
 **/
public final class Http2Client implements Http2EventListener<Http2Settings> {

    private final long connectTimeout;
    private final long applicationInitTimeout;
    private ScheduledFuture<?> connectTimeoutFuture;
    private ScheduledFuture<?> applicationInitFuture;
    private final ScheduledExecutorService scheduler;
    private Channel channel;
    private final Http2EventListener<Http2Settings> connectionListener;
    private Http2ClientInitializer initializer;

    /**
     * The constructor will setup the connection in 2 steps. The first step is waiting for the connection and HTTP/2
     * handshake to complete The second step is waiting for the SETTINGS frame to be read
     *
     * @param sslCtx Object representing the SSL Context
     * @param workerGroup The object controlling the event loop
     * @param connectListener Listener to be notified when Http2Settings objects are read
     * @param host The hostname to connect to
     * @param port The port to connect to
     * @param maxContentLength The maximum length in bytes allowed for each stream
     * @param ex Executor used to schedule time out events
     * @param connectionTimeout How many seconds to wait before timing out the connection attempt
     * @param appInitTimeout How many seconds to wait before the HTTP/2 application initialization
     *                       and SETTINGS frame have been received
     */
    public Http2Client(SslContext sslCtx, EventLoopGroup workerGroup,
                    Http2EventListener<Http2Settings> connectListener, String host, int port, int maxContentLength,
                    final ScheduledExecutorService ex, long connectionTimeout, long appInitTimeout) {
        this.scheduler = ex;
        channel = null;
        connectTimeout = connectionTimeout;
        applicationInitTimeout = appInitTimeout;
        this.connectionListener = connectListener;

        workerGroup = new NioEventLoopGroup();
        initializer = new Http2ClientInitializer(sslCtx, maxContentLength, this);

        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.handler(initializer);

        ChannelFuture channelFuture = b.connect(host, port);
        channelFuture.addListener(new GenericFutureListener<ChannelFuture>() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                connectTimeoutFuture.cancel(false);
                connectTimeoutFuture = null;

                channel = future.channel();

                // This class is already registered for http2settings, so set a timer
                applicationInitFuture = scheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        connectionListener.fail(new TimeoutException("Application protocol exchange timeout"),
                                        Http2Settings.class);
                    }
                }, applicationInitTimeout, TimeUnit.SECONDS);
            }
        });

        connectTimeoutFuture = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                connectionListener.fail(new TimeoutException("Connection timeout"), Http2Settings.class);
            }
        }, connectTimeout, TimeUnit.SECONDS);
    }

    /**
     * Get response handler from channel pipeline
     *
     * @return The response handler from channel pipeline
     */
    public HttpResponseHandler responseHandler() {
        return initializer.responseHandler();
    }

    /**
     * Close any open channels and outstanding future objects and timers
     */
    public void close() {
        if (connectTimeoutFuture != null) {
            connectTimeoutFuture.cancel(true);
            connectTimeoutFuture = null;
        }
        if (applicationInitFuture != null) {
            applicationInitFuture.cancel(true);
            applicationInitFuture = null;
        }
        if (channel != null) {
            channel.close().syncUninterruptibly();
            channel = null;
        }
    }

    /**
     * New {@link ChannelPromise} instance from the {@link Channel}
     *
     * @return New {@link ChannelPromise} instance from if {@link Channel} not equal null, or null
     */
    public ChannelPromise newPromise() {
        if (channel != null) {
            return channel.newPromise();
        }
        return null;
    }

    /**
     * Propagate the HttpSettings notification to the future object
     *
     * @param obj
     */
    @Override
    public void done(Http2Settings obj) {
        applicationInitFuture.cancel(false);
        applicationInitFuture = null;
        connectionListener.done(obj);
    }

    /**
     * Propagate the HttpSettings failure notification to the future object
     *
     * @param obj
     */
    @Override
    public void fail(Throwable obj, Class<Http2Settings> expectedType) {
        applicationInitFuture.cancel(false);
        applicationInitFuture = null;
        connectionListener.fail(obj, expectedType);
    }

    /**
     * Write and flush a FullHttpRequest to the channel
     *
     * @param request
     *            The HTTP/1.x request to send.
     * @return The channel future returned by the channel
     */
    public ChannelFuture issueHttpRequest(FullHttpRequest request) {
        return channel.writeAndFlush(request);
    }
}
