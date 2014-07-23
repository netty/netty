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

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http2.Http2OrHttpChooser.SelectedProtocol;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2EventListener;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An HTTP2 client that allows you to send HTTP2 frames to a server. Inbound and outbound frames are logged. When run
 * from the command-line, you have the option to send 2 GET requests to the server.
 */
public class Http2ClientMain implements Http2EventListener<Http2Settings> {
    private EventLoopGroup workerGroup;
    private ScheduledExecutorService ex;
    private Http2Client client;
    private boolean isConnected;
    private final int connectTimeout;
    private final int appInitTimeout;

    public Http2ClientMain(SslContext sslCtx, String host, int port) {
        workerGroup = new NioEventLoopGroup();
        ex = Executors.newSingleThreadScheduledExecutor();
        connectTimeout = 5;
        appInitTimeout = 5;
        isConnected = false;
        client = new Http2Client(sslCtx, workerGroup, this, host, port, Integer.MAX_VALUE, ex, connectTimeout,
                        appInitTimeout);
    }

    static final boolean SSL = System.getProperty("ssl") != null;
    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", SSL ? "8443" : "8080"));
    final String URL1 = System.getProperty("url1", "/index.html");
    final String URL2 = System.getProperty("url2");

    public static void main(String[] args) throws Exception {
        final SslContext sslCtx;
        if (SSL) {
            sslCtx = SslContext.newClientContext(null, InsecureTrustManagerFactory.INSTANCE, null,
                            Arrays.asList(SelectedProtocol.HTTP_2.protocolName()), 0, 0);
        } else {
            sslCtx = null;
        }
        Http2ClientMain main = new Http2ClientMain(sslCtx, HOST, PORT);
        main.sync();
    }

    /**
     * Notification that a Http2Settings has been received
     *
     * @param obj
     *            The Http2Settings
     */
    @Override
    public void done(Http2Settings obj) {
        HttpResponseHandler responseHandler = client.responseHandler();
        DefaultFullHttpRequest request = null;
        int expectedResponseStreamId = 3;
        if (URL1 != null) {
            request = new DefaultFullHttpRequest(HTTP_1_1, GET, URL1);
            request.headers().add(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
            client.issueHttpRequest(request);
            responseHandler.put(expectedResponseStreamId, client.newPromise());
            expectedResponseStreamId += 2;
        }
        if (URL2 != null) {
            request = new DefaultFullHttpRequest(HTTP_1_1, GET, URL2);
            request.headers().add(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
            client.issueHttpRequest(request);
            responseHandler.put(expectedResponseStreamId, client.newPromise());
            expectedResponseStreamId += 2;
        }
        synchronized (this) {
            isConnected = true;
            notifyAll();
        }
    }

    /**
     * Notification of failure while waiting for a Http2Settings
     *
     * @param obj
     *            The reason
     * @param expectedType
     *            Http2Settings
     */
    @Override
    public void fail(Throwable obj, Class<Http2Settings> expectedType) {
        obj.printStackTrace();
        synchronized (this) {
            notifyAll();
        }
    }

    public void sync() throws InterruptedException {
        synchronized (this) {
            wait(TimeUnit.MILLISECONDS.convert(connectTimeout + appInitTimeout + 1, TimeUnit.SECONDS));
        }

        if (isConnected) {
            client.responseHandler().awaitResponses(5, TimeUnit.SECONDS);
        }
        client.close();
    }
}
