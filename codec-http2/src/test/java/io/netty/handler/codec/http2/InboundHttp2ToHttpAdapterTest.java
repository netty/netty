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
package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2TestUtil.as;
import static io.netty.handler.codec.http2.Http2TestUtil.runInChannel;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2TestUtil.Http2Runnable;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Testing the {@link InboundHttp2ToHttpPriorityAdapter} and base class {@link InboundHttp2ToHttpAdapter} for HTTP/2
 * frames into {@link HttpObject}s
 */
public class InboundHttp2ToHttpAdapterTest {
    private List<FullHttpMessage> capturedRequests;
    private List<FullHttpMessage> capturedResponses;

    @Mock
    private HttpResponseListener serverListener;

    @Mock
    private HttpResponseListener clientListener;

    private Http2FrameWriter frameWriter;
    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private Channel serverConnectedChannel;
    private Channel clientChannel;
    private CountDownLatch serverLatch;
    private CountDownLatch clientLatch;
    private int maxContentLength;
    private HttpResponseDelegator serverDelegator;
    private HttpResponseDelegator clientDelegator;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        clientDelegator = null;
        serverDelegator = null;
        serverConnectedChannel = null;
        maxContentLength = 1024;
        setServerLatch(1);
        setClientLatch(1);
        frameWriter = new DefaultHttp2FrameWriter();

        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                Http2Connection connection = new DefaultHttp2Connection(true);
                p.addLast(
                        "reader",
                        new HttpAdapterFrameAdapter(connection, InboundHttp2ToHttpPriorityAdapter.newInstance(
                                connection, maxContentLength), new CountDownLatch(10)));
                serverDelegator = new HttpResponseDelegator(serverListener, serverLatch);
                p.addLast(serverDelegator);
                serverConnectedChannel = ch;
            }
        });

        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                Http2Connection connection = new DefaultHttp2Connection(false);
                p.addLast(
                        "reader",
                        new HttpAdapterFrameAdapter(connection, InboundHttp2ToHttpPriorityAdapter.newInstance(
                                connection, maxContentLength), new CountDownLatch(10)));
                clientDelegator = new HttpResponseDelegator(clientListener, clientLatch);
                p.addLast(clientDelegator);
            }
        });

        serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
    }

    @After
    public void teardown() throws Exception {
        cleanupCapturedRequests();
        cleanupCapturedResponses();
        serverChannel.close().sync();
        Future<?> serverGroup = sb.group().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        Future<?> serverChildGroup = sb.childGroup().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        Future<?> clientGroup = cb.group().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        serverGroup.sync();
        serverChildGroup.sync();
        clientGroup.sync();
        clientDelegator = null;
        serverDelegator = null;
        clientChannel = null;
        serverChannel = null;
        serverConnectedChannel = null;
    }

    @Test
    public void clientRequestSingleHeaderNoDataFrames() throws Exception {
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/some/path/resource2", true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.SCHEME.text(), "https");
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.AUTHORITY.text(), "example.org");
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, 0);
            final Http2Headers http2Headers =
                    new DefaultHttp2Headers().method(as("GET")).scheme(as("https"))
                            .authority(as("example.org"))
                            .path(as("/some/path/resource2"));
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
        } finally {
           request.release();
        }
    }

    @Test
    public void clientRequestOneDataFrame() throws Exception {
        final String text = "hello world";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/some/path/resource2", content, true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(as("GET"))
                    .path(as("/some/path/resource2"));
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    frameWriter.writeData(ctxClient(), 3, content.retain(), 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
        } finally {
            request.release();
        }
    }

    @Test
    public void clientRequestMultipleDataFrames() throws Exception {
        final String text = "hello world big time data!";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/some/path/resource2", content, true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(as("GET"))
                    .path(as("/some/path/resource2"));
            final int midPoint = text.length() / 2;
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    frameWriter
                    .writeData(ctxClient(), 3, content.slice(0, midPoint).retain(), 0, false, newPromiseClient());
                    frameWriter.writeData(ctxClient(), 3, content.slice(midPoint, text.length() - midPoint).retain(), 0,
                            true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
        } finally {
            request.release();
        }
    }

    @Test
    public void clientRequestMultipleEmptyDataFrames() throws Exception {
        final String text = "";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/some/path/resource2", content, true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(as("GET"))
                    .path(as("/some/path/resource2"));
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    frameWriter.writeData(ctxClient(), 3, content.retain(), 0, false, newPromiseClient());
                    frameWriter.writeData(ctxClient(), 3, content.retain(), 0, false, newPromiseClient());
                    frameWriter.writeData(ctxClient(), 3, content.retain(), 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
        } finally {
            request.release();
        }
    }

    @Test
    public void clientRequestMultipleHeaders() throws Exception {
        // writeHeaders will implicitly add an END_HEADERS tag each time and so this test does not follow the HTTP
        // message flow. We currently accept this message flow and just add the second headers to the trailing headers.
        final String text = "";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/some/path/resource2", content, true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
            HttpHeaders trailingHeaders = request.trailingHeaders();
            trailingHeaders.set("FoO", "goo");
            trailingHeaders.set("foO2", "goo2");
            trailingHeaders.add("fOo2", "goo3");
            final Http2Headers http2Headers =
                    new DefaultHttp2Headers().method(as("GET")).path(
                            as("/some/path/resource2"));
            final Http2Headers http2Headers2 =
                    new DefaultHttp2Headers().set(as("foo"), as("goo"))
                            .set(as("foo2"), as("goo2"))
                            .add(as("foo2"), as("goo3"));
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    frameWriter.writeHeaders(ctxClient(), 3, http2Headers2, 0, false, newPromiseClient());
                    frameWriter.writeData(ctxClient(), 3, content.retain(), 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
        } finally {
            request.release();
        }
    }

    @Test
    public void clientRequestTrailingHeaders() throws Exception {
        final String text = "some data";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/some/path/resource2", content, true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
            HttpHeaders trailingHeaders = request.trailingHeaders();
            trailingHeaders.set("Foo", "goo");
            trailingHeaders.set("fOo2", "goo2");
            trailingHeaders.add("foO2", "goo3");
            final Http2Headers http2Headers =
                    new DefaultHttp2Headers().method(as("GET")).path(
                            as("/some/path/resource2"));
            final Http2Headers http2Headers2 =
                    new DefaultHttp2Headers().set(as("foo"), as("goo"))
                            .set(as("foo2"), as("goo2"))
                            .add(as("foo2"), as("goo3"));
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    frameWriter.writeData(ctxClient(), 3, content.retain(), 0, false, newPromiseClient());
                    frameWriter.writeHeaders(ctxClient(), 3, http2Headers2, 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
        } finally {
            request.release();
        }
    }

    @Test
    public void clientRequestStreamDependencyInHttpMessageFlow() throws Exception {
        setServerLatch(2);
        final String text = "hello world big time data!";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final String text2 = "hello world big time data...number 2!!";
        final ByteBuf content2 = Unpooled.copiedBuffer(text2.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT,
                "/some/path/resource", content, true);
        final FullHttpMessage request2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT,
                "/some/path/resource2", content2, true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
            HttpHeaders httpHeaders2 = request2.headers();
            httpHeaders2.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
            httpHeaders2.set(HttpUtil.ExtensionHeaderNames.STREAM_DEPENDENCY_ID.text(), 3);
            httpHeaders2.set(HttpUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), 123);
            httpHeaders2.set(HttpHeaders.Names.CONTENT_LENGTH, text2.length());
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(as("PUT"))
                    .path(as("/some/path/resource"));
            final Http2Headers http2Headers2 = new DefaultHttp2Headers().method(as("PUT"))
                    .path(as("/some/path/resource2"));
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    frameWriter.writeHeaders(ctxClient(), 5, http2Headers2, 0, false, newPromiseClient());
                    frameWriter.writePriority(ctxClient(), 5, 3, (short) 123, true, newPromiseClient());
                    frameWriter.writeData(ctxClient(), 3, content.retain(), 0, true, newPromiseClient());
                    frameWriter.writeData(ctxClient(), 5, content2.retain(), 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> httpObjectCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener, times(2)).messageReceived(httpObjectCaptor.capture());
            capturedRequests = httpObjectCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
            assertEquals(request2, capturedRequests.get(1));
        } finally {
            request.release();
            request2.release();
        }
    }

    @Test
    public void clientRequestStreamDependencyOutsideHttpMessageFlow() throws Exception {
        setServerLatch(3);
        final String text = "hello world big time data!";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final String text2 = "hello world big time data...number 2!!";
        final ByteBuf content2 = Unpooled.copiedBuffer(text2.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT,
                "/some/path/resource", content, true);
        final FullHttpMessage request2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT,
                "/some/path/resource2", content2, true);
        final FullHttpMessage request3 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpUtil.OUT_OF_MESSAGE_SEQUENCE_METHOD, HttpUtil.OUT_OF_MESSAGE_SEQUENCE_PATH, true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
            HttpHeaders httpHeaders2 = request2.headers();
            httpHeaders2.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
            httpHeaders2.set(HttpHeaders.Names.CONTENT_LENGTH, text2.length());
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(as("PUT"))
                    .path(as("/some/path/resource"));
            final Http2Headers http2Headers2 = new DefaultHttp2Headers().method(as("PUT"))
                    .path(as("/some/path/resource2"));
            HttpHeaders httpHeaders3 = request3.headers();
            httpHeaders3.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
            httpHeaders3.set(HttpUtil.ExtensionHeaderNames.STREAM_DEPENDENCY_ID.text(), 3);
            httpHeaders3.set(HttpUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), 222);
            httpHeaders3.set(HttpHeaders.Names.CONTENT_LENGTH, 0);
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    frameWriter.writeHeaders(ctxClient(), 5, http2Headers2, 0, false, newPromiseClient());
                    frameWriter.writeData(ctxClient(), 3, content.retain(), 0, true, newPromiseClient());
                    frameWriter.writeData(ctxClient(), 5, content2.retain(), 0, true, newPromiseClient());
                    frameWriter.writePriority(ctxClient(), 5, 3, (short) 222, false, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> httpObjectCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener, times(3)).messageReceived(httpObjectCaptor.capture());
            capturedRequests = httpObjectCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
            assertEquals(request2, capturedRequests.get(1));
            assertEquals(request3, capturedRequests.get(2));
        } finally {
            request.release();
            request2.release();
            request3.release();
        }
    }

    @Test
    public void serverRequestPushPromise() throws Exception {
        setClientLatch(2);
        final String text = "hello world big time data!";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final String text2 = "hello world smaller data?";
        final ByteBuf content2 = Unpooled.copiedBuffer(text2.getBytes());
        final FullHttpMessage response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                content, true);
        final FullHttpMessage response2 = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CREATED,
                content2, true);
        final FullHttpMessage request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET, "/push/test", true);
        try {
            HttpHeaders httpHeaders = response.headers();
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
            HttpHeaders httpHeaders2 = response2.headers();
            httpHeaders2.set(HttpUtil.ExtensionHeaderNames.SCHEME.text(), "https");
            httpHeaders2.set(HttpUtil.ExtensionHeaderNames.AUTHORITY.text(), "example.org");
            httpHeaders2.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
            httpHeaders2.set(HttpUtil.ExtensionHeaderNames.STREAM_PROMISE_ID.text(), 3);
            httpHeaders2.set(HttpHeaders.Names.CONTENT_LENGTH, text2.length());

            httpHeaders = request.headers();
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, 0);
            final Http2Headers http2Headers3 = new DefaultHttp2Headers().method(as("GET"))
                    .path(as("/push/test"));
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeHeaders(ctxClient(), 3, http2Headers3, 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));

            final Http2Headers http2Headers = new DefaultHttp2Headers().status(as("200"));
            final Http2Headers http2Headers2 =
                    new DefaultHttp2Headers().status(as("201")).scheme(as("https"))
                            .authority(as("example.org"));
            runInChannel(serverConnectedChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeHeaders(ctxServer(), 3, http2Headers, 0, false, newPromiseServer());
                    frameWriter.writePushPromise(ctxServer(), 3, 5, http2Headers2, 0, newPromiseServer());
                    frameWriter.writeData(ctxServer(), 3, content.retain(), 0, true, newPromiseServer());
                    frameWriter.writeData(ctxServer(), 5, content2.retain(), 0, true, newPromiseServer());
                    ctxServer().flush();
                }
            });
            awaitResponses();
            ArgumentCaptor<FullHttpMessage> responseCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(clientListener, times(2)).messageReceived(responseCaptor.capture());
            capturedResponses = responseCaptor.getAllValues();
            assertEquals(response, capturedResponses.get(0));
            assertEquals(response2, capturedResponses.get(1));
        } finally {
            request.release();
            response.release();
            response2.release();
        }
    }

    @Test
    public void serverResponseHeaderInformational() throws Exception {
        final FullHttpMessage request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/info/test",
                true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
        httpHeaders.set(HttpHeaders.Names.EXPECT, HttpHeaders.Values.CONTINUE);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, 0);
        final Http2Headers http2Headers =
                new DefaultHttp2Headers()
                        .method(as("PUT"))
                        .path(as("/info/test"))
                        .set(as(HttpHeaders.Names.EXPECT.toString()),
                                as(HttpHeaders.Values.CONTINUE.toString()));
        final FullHttpMessage response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE);
        final String text = "a big payload";
        final ByteBuf payload = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpMessage request2 = request.copy(payload);
        final FullHttpMessage response2 = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        try {
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
            cleanupCapturedRequests();
            reset(serverListener);

            httpHeaders = response.headers();
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, 0);
            final Http2Headers http2HeadersResponse = new DefaultHttp2Headers().status(as("100"));
            runInChannel(serverConnectedChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeHeaders(ctxServer(), 3, http2HeadersResponse, 0, false, newPromiseServer());
                    ctxServer().flush();
                }
            });
            awaitResponses();
            ArgumentCaptor<FullHttpMessage> responseCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(clientListener).messageReceived(responseCaptor.capture());
            capturedResponses = responseCaptor.getAllValues();
            assertEquals(response, capturedResponses.get(0));
            cleanupCapturedResponses();
            reset(clientListener);

            setServerLatch(1);
            httpHeaders = request2.headers();
            httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
            httpHeaders.remove(HttpHeaders.Names.EXPECT);
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeData(ctxClient(), 3, payload.retain(), 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitRequests();
            requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request2, capturedRequests.get(0));

            setClientLatch(1);
            httpHeaders = response2.headers();
            httpHeaders.set(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, 0);
            final Http2Headers http2HeadersResponse2 = new DefaultHttp2Headers().status(as("200"));
            runInChannel(serverConnectedChannel, new Http2Runnable() {
                @Override
                public void run() {
                    frameWriter.writeHeaders(ctxServer(), 3, http2HeadersResponse2, 0, true, newPromiseServer());
                    ctxServer().flush();
                }
            });
            awaitResponses();
            responseCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(clientListener).messageReceived(responseCaptor.capture());
            capturedResponses = responseCaptor.getAllValues();
            assertEquals(response2, capturedResponses.get(0));
        } finally {
            request.release();
            request2.release();
            response.release();
            response2.release();
        }
    }

    private void cleanupCapturedRequests() {
        if (capturedRequests != null) {
            for (int i = 0; i < capturedRequests.size(); ++i) {
                capturedRequests.get(i).release();
            }
            capturedRequests = null;
        }
    }

    private void cleanupCapturedResponses() {
        if (capturedResponses != null) {
            for (int i = 0; i < capturedResponses.size(); ++i) {
                capturedResponses.get(i).release();
            }
            capturedResponses = null;
        }
    }

    private void setServerLatch(int count) {
        serverLatch = new CountDownLatch(count);
        if (serverDelegator != null) {
            serverDelegator.latch(serverLatch);
        }
    }

    private void setClientLatch(int count) {
        clientLatch = new CountDownLatch(count);
        if (clientDelegator != null) {
            clientDelegator.latch(clientLatch);
        }
    }

    private void awaitRequests() throws Exception {
        serverLatch.await(2, SECONDS);
    }

    private void awaitResponses() throws Exception {
        clientLatch.await(2, SECONDS);
    }

    private ChannelHandlerContext ctxClient() {
        return clientChannel.pipeline().firstContext();
    }

    private ChannelPromise newPromiseClient() {
        return ctxClient().newPromise();
    }

    private ChannelHandlerContext ctxServer() {
        return serverConnectedChannel.pipeline().firstContext();
    }

    private ChannelPromise newPromiseServer() {
        return ctxServer().newPromise();
    }

    private interface HttpResponseListener {
        void messageReceived(HttpObject obj);
    }

    private final class HttpResponseDelegator extends SimpleChannelInboundHandler<HttpObject> {
        private final HttpResponseListener listener;
        private CountDownLatch latch;

        public HttpResponseDelegator(HttpResponseListener listener, CountDownLatch latch) {
            super(false);
            this.listener = listener;
            this.latch = latch;
        }

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            this.listener.messageReceived(msg);
            this.latch.countDown();
        }

        public void latch(CountDownLatch latch) {
            this.latch = latch;
        }
    }

    private final class HttpAdapterFrameAdapter extends Http2TestUtil.FrameAdapter {
        HttpAdapterFrameAdapter(Http2Connection connection, Http2FrameListener listener, CountDownLatch latch) {
            super(connection, listener, latch, false);
        }

        @Override
        protected void closeStream(Http2Stream stream, boolean dataRead) {
            if (!dataRead) { // NOTE: Do not close the stream to allow the out of order messages to be processed
                super.closeStream(stream, dataRead);
            }
        }
    }
}
