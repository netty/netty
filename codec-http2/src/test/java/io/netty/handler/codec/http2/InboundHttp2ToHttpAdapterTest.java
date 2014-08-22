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

import static io.netty.handler.codec.http2.Http2TestUtil.runInChannel;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.reset;
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
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2TestUtil.Http2Runnable;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Testing the {@link InboundHttp2ToHttpAdapter} for HTTP/2 frames into {@link HttpObject}s
 */
public class InboundHttp2ToHttpAdapterTest {

    @Mock
    private HttpResponseListener serverObserver;

    @Mock
    private HttpResponseListener clientObserver;

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
                p.addLast("reader",
                                new FrameAdapter(InboundHttp2ToHttpAdapter.newInstance(connection, maxContentLength),
                                                new CountDownLatch(10)));
                serverDelegator = new HttpResponseDelegator(serverObserver, serverLatch);
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
                p.addLast("reader",
                                new FrameAdapter(InboundHttp2ToHttpAdapter.newInstance(connection, maxContentLength),
                                                new CountDownLatch(10)));
                clientDelegator = new HttpResponseDelegator(clientObserver, clientLatch);
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
        serverChannel.close().sync();
        sb.group().shutdownGracefully();
        cb.group().shutdownGracefully();
        clientDelegator = null;
        serverDelegator = null;
        clientChannel = null;
        serverChannel = null;
        serverConnectedChannel = null;
    }

    @Test
    public void clientRequestSingleHeaderNoDataFrames() throws Exception {
        final HttpMessage request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                        "/some/path/resource2", true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2ToHttpHeaders.Names.SCHEME, "https");
        httpHeaders.set(Http2ToHttpHeaders.Names.AUTHORITY, "example.org");
        httpHeaders.set(Http2ToHttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, 0);
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder().method("GET").scheme("https")
                        .authority("example.org").path("/some/path/resource2").build();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, true, newPromiseClient());
                ctxClient().flush();
            }
        });
        awaitRequests();
        verify(serverObserver).messageReceived(eq(request));
    }

    @Test
    public void clientRequestOneDataFrame() throws Exception {
        final String text = "hello world";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                        "/some/path/resource2", content, true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2ToHttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder().method("GET").path("/some/path/resource2")
                        .build();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                frameWriter.writeData(ctxClient(), 3, Unpooled.copiedBuffer(text.getBytes()), 0, true,
                                newPromiseClient());
                ctxClient().flush();
            }
        });
        awaitRequests();
        verify(serverObserver).messageReceived(eq(request));
        request.release();
    }

    @Test
    public void clientRequestMultipleDataFrames() throws Exception {
        final String text = "hello world big time data!";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                        "/some/path/resource2", content, true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2ToHttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder().method("GET").path("/some/path/resource2")
                        .build();
        final int midPoint = text.length() / 2;
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                frameWriter.writeData(ctxClient(), 3, content.slice(0, midPoint).retain(), 0,
                                false, newPromiseClient());
                frameWriter.writeData(ctxClient(), 3, content.slice(midPoint, text.length() - midPoint).retain(), 0,
                                true, newPromiseClient());
                ctxClient().flush();
            }
        });
        awaitRequests();
        verify(serverObserver).messageReceived(eq(request));
        request.release();
    }

    @Test
    public void clientRequestMultipleEmptyDataFrames() throws Exception {
        final String text = "";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                        "/some/path/resource2", content, true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2ToHttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder().method("GET").path("/some/path/resource2")
                        .build();
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
        verify(serverObserver).messageReceived(eq(request));
        request.release();
    }

    @Test
    public void clientRequestMultipleHeaders() throws Exception {
        // writeHeaders will implicitly add an END_HEADERS tag each time and so this test does not follow the HTTP
        // message flow. We currently accept this message flow and just add the second headers to the trailing headers.
        final String text = "";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                        "/some/path/resource2", content, true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2ToHttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
        HttpHeaders trailingHeaders = request.trailingHeaders();
        trailingHeaders.set("FoO", "goo");
        trailingHeaders.set("foO2", "goo2");
        trailingHeaders.add("fOo2", "goo3");
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder().method("GET").path("/some/path/resource2")
                        .build();
        final Http2Headers http2Headers2 = new DefaultHttp2Headers.Builder().set("foo", "goo").set("foo2", "goo2")
                        .add("foo2", "goo3").build();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                frameWriter.writeHeaders(ctxClient(), 3, http2Headers2, 0, false, newPromiseClient());
                frameWriter.writeData(ctxClient(), 3, Unpooled.copiedBuffer(text.getBytes()), 0, true,
                                newPromiseClient());
                ctxClient().flush();
            }
        });
        awaitRequests();
        verify(serverObserver).messageReceived(eq(request));
        request.release();
    }

    @Test
    public void clientRequestTrailingHeaders() throws Exception {
        final String text = "some data";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                        "/some/path/resource2", content, true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2ToHttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
        HttpHeaders trailingHeaders = request.trailingHeaders();
        trailingHeaders.set("Foo", "goo");
        trailingHeaders.set("fOo2", "goo2");
        trailingHeaders.add("foO2", "goo3");
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder().method("GET").path("/some/path/resource2")
                        .build();
        final Http2Headers http2Headers2 = new DefaultHttp2Headers.Builder().set("foo", "goo").set("foo2", "goo2")
                        .add("foo2", "goo3").build();
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
        verify(serverObserver).messageReceived(eq(request));
        request.release();
    }

    @Test
    public void clientRequestStreamDependency() throws Exception {
        setServerLatch(2);
        final String text = "hello world big time data!";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final String text2 = "hello world big time data...number 2!!";
        final ByteBuf content2 = Unpooled.copiedBuffer(text2.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT,
                        "/some/path/resource", content, true);

        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2ToHttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
        final FullHttpMessage request2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT,
                        "/some/path/resource2", content2, true);
        HttpHeaders httpHeaders2 = request2.headers();
        httpHeaders2.set(Http2ToHttpHeaders.Names.STREAM_ID, 5);
        httpHeaders2.set(Http2ToHttpHeaders.Names.STREAM_DEPENDENCY_ID, 3);
        httpHeaders2.set(Http2ToHttpHeaders.Names.STREAM_EXCLUSIVE, true);
        httpHeaders2.set(Http2ToHttpHeaders.Names.STREAM_WEIGHT, 256);
        httpHeaders2.set(HttpHeaders.Names.CONTENT_LENGTH, text2.length());
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder().method("PUT").path("/some/path/resource")
                        .build();
        final Http2Headers http2Headers2 = new DefaultHttp2Headers.Builder().method("PUT").path("/some/path/resource2")
                        .build();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                frameWriter.writeHeaders(ctxClient(), 5, http2Headers2, 0, false, newPromiseClient());
                frameWriter.writePriority(ctxClient(), 5, 3, (short) 256, true, newPromiseClient());
                frameWriter.writeData(ctxClient(), 3, content.retain(), 0, true, newPromiseClient());
                frameWriter.writeData(ctxClient(), 5, content2.retain(), 0, true, newPromiseClient());
                ctxClient().flush();
            }
        });
        awaitRequests();
        ArgumentCaptor<HttpObject> httpObjectCaptor = ArgumentCaptor.forClass(HttpObject.class);
        verify(serverObserver, times(2)).messageReceived(httpObjectCaptor.capture());
        List<HttpObject> capturedHttpObjects = httpObjectCaptor.getAllValues();
        assertEquals(request, capturedHttpObjects.get(0));
        assertEquals(request2, capturedHttpObjects.get(1));
        request.release();
        request2.release();
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
        HttpHeaders httpHeaders = response.headers();
        httpHeaders.set(Http2ToHttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
        final FullHttpMessage response2 = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CREATED,
                        content2, true);
        HttpHeaders httpHeaders2 = response2.headers();
        httpHeaders2.set(Http2ToHttpHeaders.Names.SCHEME, "https");
        httpHeaders2.set(Http2ToHttpHeaders.Names.AUTHORITY, "example.org");
        httpHeaders2.set(Http2ToHttpHeaders.Names.STREAM_ID, 5);
        httpHeaders2.set(Http2ToHttpHeaders.Names.STREAM_PROMISE_ID, 3);
        httpHeaders2.set(HttpHeaders.Names.CONTENT_LENGTH, text2.length());

        final HttpMessage request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/push/test", true);
        httpHeaders = request.headers();
        httpHeaders.set(Http2ToHttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, 0);
        final Http2Headers http2Headers3 = new DefaultHttp2Headers.Builder().method("GET").path("/push/test").build();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctxClient(), 3, http2Headers3, 0, true, newPromiseClient());
                ctxClient().flush();
            }
        });
        awaitRequests();
        verify(serverObserver).messageReceived(eq(request));

        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder().status("200").build();
        final Http2Headers http2Headers2 = new DefaultHttp2Headers.Builder().status("201").scheme("https")
                        .authority("example.org").build();
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
        ArgumentCaptor<HttpObject> httpObjectCaptor = ArgumentCaptor.forClass(HttpObject.class);
        verify(clientObserver, times(2)).messageReceived(httpObjectCaptor.capture());
        List<HttpObject> capturedHttpObjects = httpObjectCaptor.getAllValues();
        assertEquals(response, capturedHttpObjects.get(0));
        assertEquals(response2, capturedHttpObjects.get(1));
        response.release();
        response2.release();
    }

    @Test
    public void serverResponseHeaderInformational() throws Exception {
        final FullHttpMessage request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/info/test",
                        true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2ToHttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set(HttpHeaders.Names.EXPECT, HttpHeaders.Values.CONTINUE);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, 0);
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder().method("PUT").path("/info/test")
                        .set(HttpHeaders.Names.EXPECT.toString(), HttpHeaders.Values.CONTINUE).build();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                ctxClient().flush();
            }
        });
        awaitRequests();
        verify(serverObserver).messageReceived(eq(request));
        reset(serverObserver);

        final FullHttpMessage response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE);
        httpHeaders = response.headers();
        httpHeaders.set(Http2ToHttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, 0);
        final Http2Headers http2HeadersResponse = new DefaultHttp2Headers.Builder().status("100").build();
        runInChannel(serverConnectedChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctxServer(), 3, http2HeadersResponse, 0, false, newPromiseServer());
                ctxServer().flush();
            }
        });
        awaitResponses();
        verify(clientObserver).messageReceived(eq(response));
        reset(clientObserver);

        setServerLatch(1);
        final String text = "a big payload";
        final ByteBuf payload = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpMessage request2 = request.copy(payload);
        httpHeaders = request2.headers();
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, text.length());
        httpHeaders.remove(HttpHeaders.Names.EXPECT);
        request.release();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeData(ctxClient(), 3, payload.retain(), 0, true, newPromiseClient());
                ctxClient().flush();
            }
        });
        awaitRequests();
        verify(serverObserver).messageReceived(eq(request2));
        request2.release();

        setClientLatch(1);
        final FullHttpMessage response2 = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        httpHeaders = response2.headers();
        httpHeaders.set(Http2ToHttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, 0);
        final Http2Headers http2HeadersResponse2 = new DefaultHttp2Headers.Builder().status("200").build();
        runInChannel(serverConnectedChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctxServer(), 3, http2HeadersResponse2, 0, true, newPromiseServer());
                ctxServer().flush();
            }
        });
        awaitResponses();
        verify(clientObserver).messageReceived(eq(response2));
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
        serverLatch.await(200, SECONDS);
    }

    private void awaitResponses() throws Exception {
        clientLatch.await(200, SECONDS);
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

    private final class FrameAdapter extends ByteToMessageDecoder {
        private final Http2FrameObserver observer;
        private final DefaultHttp2FrameReader reader;
        private final CountDownLatch latch;

        FrameAdapter(Http2FrameObserver observer, CountDownLatch latch) {
            this.observer = observer;
            reader = new DefaultHttp2FrameReader();
            this.latch = latch;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            reader.readFrame(ctx, in, new Http2FrameObserver() {

                @Override
                public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                                boolean endOfStream) throws Http2Exception {
                    observer.onDataRead(ctx, streamId, copy(data), padding, endOfStream);
                    latch.countDown();
                }

                @Override
                public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                                boolean endStream) throws Http2Exception {
                    observer.onHeadersRead(ctx, streamId, headers, padding, endStream);
                    latch.countDown();
                }

                @Override
                public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                int streamDependency, short weight, boolean exclusive, int padding, boolean endStream)
                                throws Http2Exception {
                    observer.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding,
                                    endStream);
                    latch.countDown();
                }

                @Override
                public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                                boolean exclusive) throws Http2Exception {
                    observer.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
                    latch.countDown();
                }

                @Override
                public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                                throws Http2Exception {
                    observer.onRstStreamRead(ctx, streamId, errorCode);
                    latch.countDown();
                }

                @Override
                public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
                    observer.onSettingsAckRead(ctx);
                    latch.countDown();
                }

                @Override
                public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
                    observer.onSettingsRead(ctx, settings);
                    latch.countDown();
                }

                @Override
                public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
                    observer.onPingRead(ctx, copy(data));
                    latch.countDown();
                }

                @Override
                public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
                    observer.onPingAckRead(ctx, copy(data));
                    latch.countDown();
                }

                @Override
                public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                Http2Headers headers, int padding) throws Http2Exception {
                    observer.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
                    latch.countDown();
                }

                @Override
                public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
                                throws Http2Exception {
                    observer.onGoAwayRead(ctx, lastStreamId, errorCode, copy(debugData));
                    latch.countDown();
                }

                @Override
                public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
                                throws Http2Exception {
                    observer.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
                    latch.countDown();
                }

                @Override
                public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                                ByteBuf payload) {
                    observer.onUnknownFrame(ctx, frameType, streamId, flags, payload);
                    latch.countDown();
                }
            });
        }

        ByteBuf copy(ByteBuf buffer) {
            return Unpooled.copiedBuffer(buffer);
        }
    }
}
