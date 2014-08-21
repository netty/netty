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
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
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
    private HttpResponseListener messageObserver;

    @Mock
    private Http2FrameObserver clientObserver;

    private Http2FrameWriter frameWriter;
    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private Channel clientChannel;
    private CountDownLatch serverLatch;
    private long maxContentLength;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        maxContentLength = 1 << 16;
        serverLatch = new CountDownLatch(1);
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
                p.addLast("reader", new FrameAdapter(InboundHttp2ToHttpAdapter.newInstance(
                    connection, maxContentLength),
                    new CountDownLatch(10)));
                p.addLast(new HttpResponseDelegator(messageObserver));
            }
        });

        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast("reader", new FrameAdapter(clientObserver, new CountDownLatch(10)));
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
    }

    @Test
    public void clientRequestSingleHeaderNoDataFrames() throws Exception {
        serverLatch = new CountDownLatch(2);
        final HttpMessage request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                        HttpMethod.GET, "/some/path/resource2", true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2HttpHeaders.Names.SCHEME, "https");
        httpHeaders.set(Http2HttpHeaders.Names.AUTHORITY, "example.org");
        httpHeaders.set(Http2HttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set(HttpHeaders.Names.CONTENT_LENGTH, 0);
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder()
            .method("GET").scheme("https").authority("example.org")
            .path("/some/path/resource2").build();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctx(), 3, http2Headers, 0, true, newPromise());
                ctx().flush();
            }
        });
        awaitRequests();
        verify(messageObserver).messageReceived(eq(request));
        verify(messageObserver).messageReceived(eq(LastHttpContent.EMPTY_LAST_CONTENT));
    }

    @Test
    public void clientRequestOneDataFrame() throws Exception {
        serverLatch = new CountDownLatch(2);
        final HttpMessage request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                        HttpMethod.GET, "/some/path/resource2", true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2HttpHeaders.Names.STREAM_ID, 3);
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder()
            .method("GET").path("/some/path/resource2").build();
        final String text = "hello world";
        final HttpContent content = new DefaultLastHttpContent(Unpooled.copiedBuffer(text.getBytes()),
                                                               true);
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctx(), 3, http2Headers, 0, false, newPromise());
                frameWriter.writeData(ctx(), 3,
                    Unpooled.copiedBuffer(text.getBytes()), 0, true, newPromise());
                ctx().flush();
            }
        });
        awaitRequests();
        ArgumentCaptor<HttpObject> httpObjectCaptor = ArgumentCaptor.forClass(HttpObject.class);
        verify(messageObserver, times(2)).messageReceived(httpObjectCaptor.capture());

        List<HttpObject> capturedHttpObjects = httpObjectCaptor.getAllValues();
        assertEquals(request, capturedHttpObjects.get(0));
        assertEquals(content, capturedHttpObjects.get(1));
    }

    @Test
    public void clientRequestMultipleDataFrames() throws Exception {
        serverLatch = new CountDownLatch(3);
        final HttpMessage request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                        HttpMethod.GET, "/some/path/resource2", true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2HttpHeaders.Names.STREAM_ID, 3);
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder()
            .method("GET").path("/some/path/resource2").build();
        final String text = "hello world";
        final String text2 = "hello world2";
        final HttpContent content = new DefaultHttpContent(Unpooled.copiedBuffer(text.getBytes()));
        final HttpContent content2 = new DefaultLastHttpContent(Unpooled.copiedBuffer(text2.getBytes()),
                                                                true);
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctx(), 3, http2Headers, 0, false, newPromise());
                frameWriter.writeData(ctx(), 3,
                    Unpooled.copiedBuffer(text.getBytes()), 0, false, newPromise());
                frameWriter.writeData(ctx(), 3,
                    Unpooled.copiedBuffer(text2.getBytes()), 0, true, newPromise());
                ctx().flush();
            }
        });
        awaitRequests();
        ArgumentCaptor<HttpObject> httpObjectCaptor = ArgumentCaptor.forClass(HttpObject.class);
        verify(messageObserver, times(3)).messageReceived(httpObjectCaptor.capture());

        List<HttpObject> capturedHttpObjects = httpObjectCaptor.getAllValues();
        assertEquals(request, capturedHttpObjects.get(0));
        assertEquals(content, capturedHttpObjects.get(1));
        assertEquals(content2, capturedHttpObjects.get(2));
    }

    @Test
    public void clientRequestMultipleEmptyDataFrames() throws Exception {
        serverLatch = new CountDownLatch(4);
        final HttpMessage request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                        HttpMethod.GET, "/some/path/resource2", true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2HttpHeaders.Names.STREAM_ID, 3);
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder()
            .method("GET").path("/some/path/resource2").build();
        final String text = "";
        final HttpContent content = new DefaultHttpContent(Unpooled.copiedBuffer(text.getBytes()));
        final HttpContent content2 = new DefaultLastHttpContent(Unpooled.copiedBuffer(text.getBytes()),
                                                                true);
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctx(), 3, http2Headers, 0, false, newPromise());
                frameWriter.writeData(ctx(), 3, Unpooled.copiedBuffer(text.getBytes()), 0, false,
                        newPromise());
                frameWriter.writeData(ctx(), 3, Unpooled.copiedBuffer(text.getBytes()), 0, false,
                        newPromise());
                frameWriter.writeData(ctx(), 3, Unpooled.copiedBuffer(text.getBytes()), 0, true, newPromise());
                ctx().flush();
            }
        });
        awaitRequests();
        ArgumentCaptor<HttpObject> httpObjectCaptor = ArgumentCaptor.forClass(HttpObject.class);
        verify(messageObserver, times(4)).messageReceived(httpObjectCaptor.capture());

        List<HttpObject> capturedHttpObjects = httpObjectCaptor.getAllValues();
        assertEquals(request, capturedHttpObjects.get(0));
        assertEquals(content, capturedHttpObjects.get(1));
        assertEquals(content, capturedHttpObjects.get(2));
        assertEquals(content2, capturedHttpObjects.get(3));
    }

    @Test
    public void clientRequestHeaderContinuation() throws Exception {
        serverLatch = new CountDownLatch(2);
        final HttpMessage request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                        HttpMethod.GET, "/some/path/resource2", true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2HttpHeaders.Names.STREAM_ID, 3);
        httpHeaders.set("foo", "goo");
        httpHeaders.set("foo2", "goo2");
        httpHeaders.add("foo2", "goo3");
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder()
            .method("GET").path("/some/path/resource2").build();
        final Http2Headers http2Headers2 = new DefaultHttp2Headers.Builder().set("foo", "goo")
            .set("foo2", "goo2").add("foo2", "goo3").build();
        final String text = "";
        final HttpContent content = new DefaultLastHttpContent(Unpooled.copiedBuffer(text.getBytes()),
                                                               true);
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctx(), 3, http2Headers, 0, false, newPromise());
                frameWriter.writeHeaders(ctx(), 3, http2Headers2, 0, false, newPromise());
                frameWriter.writeData(ctx(), 3,
                    Unpooled.copiedBuffer(text.getBytes()), 0, true, newPromise());
                ctx().flush();
            }
        });
        awaitRequests();
        ArgumentCaptor<HttpObject> httpObjectCaptor = ArgumentCaptor.forClass(HttpObject.class);
        verify(messageObserver, times(2)).messageReceived(httpObjectCaptor.capture());

        List<HttpObject> capturedHttpObjects = httpObjectCaptor.getAllValues();
        assertEquals(request, capturedHttpObjects.get(0));
        assertEquals(content, capturedHttpObjects.get(1));
    }

    @Test
    public void clientRequestTrailingHeaders() throws Exception {
        serverLatch = new CountDownLatch(3);
        final HttpMessage request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                        HttpMethod.GET, "/some/path/resource2", true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2HttpHeaders.Names.STREAM_ID, 3);
        final LastHttpContent trailer = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, true);
        HttpHeaders trailingHeaders = trailer.trailingHeaders();
        trailingHeaders.set("foo", "goo");
        trailingHeaders.set("foo2", "goo2");
        trailingHeaders.add("foo2", "goo3");
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder()
            .method("GET").path("/some/path/resource2").build();
        final Http2Headers http2Headers2 = new DefaultHttp2Headers.Builder().set("foo", "goo")
            .set("foo2", "goo2").add("foo2", "goo3").build();
        final String text = "not empty!";
        final HttpContent content = new DefaultLastHttpContent(Unpooled.copiedBuffer(text.getBytes()),
                                                               true);
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctx(), 3, http2Headers, 0, false, newPromise());
                frameWriter.writeData(ctx(), 3,
                    Unpooled.copiedBuffer(text.getBytes()), 0, false, newPromise());
                frameWriter.writeHeaders(ctx(), 3, http2Headers2, 0, true, newPromise());
                ctx().flush();
            }
        });
        awaitRequests();
        ArgumentCaptor<HttpObject> httpObjectCaptor = ArgumentCaptor.forClass(HttpObject.class);
        verify(messageObserver, times(3)).messageReceived(httpObjectCaptor.capture());

        List<HttpObject> capturedHttpObjects = httpObjectCaptor.getAllValues();
        assertEquals(request, capturedHttpObjects.get(0));
        assertEquals(content, capturedHttpObjects.get(1));
        assertEquals(trailer, capturedHttpObjects.get(2));
    }

    @Test
    public void clientRequestPushPromise() throws Exception {
        serverLatch = new CountDownLatch(4);
        final HttpMessage request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                        HttpMethod.GET, "/some/path/resource", true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2HttpHeaders.Names.STREAM_ID, 3);
        final HttpMessage request2 = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                        HttpMethod.GET, "/some/path/resource2", true);
        HttpHeaders httpHeaders2 = request2.headers();
        httpHeaders2.set(Http2HttpHeaders.Names.SCHEME, "https");
        httpHeaders2.set(Http2HttpHeaders.Names.AUTHORITY, "example.org");
        httpHeaders2.set(Http2HttpHeaders.Names.STREAM_ID, 5);
        httpHeaders2.set(Http2HttpHeaders.Names.STREAM_PROMISE_ID, 3);
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder()
            .method("GET").path("/some/path/resource").build();
        final Http2Headers http2Headers2 = new DefaultHttp2Headers.Builder()
            .method("GET").path("/some/path/resource2").scheme("https")
            .authority("example.org").build();
        final String text = "hello 1!**";
        final HttpContent content = new DefaultLastHttpContent(Unpooled.copiedBuffer(text.getBytes()),
                                                               true);
        final String text2 = "hello 2!><";
        final HttpContent content2 = new DefaultLastHttpContent(Unpooled.copiedBuffer(text2.getBytes()),
                                                                true);
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctx(), 3, http2Headers, 0, false, newPromise());
                frameWriter.writePushPromise(ctx(), 3, 5, http2Headers2, 0, newPromise());
                frameWriter.writeData(ctx(), 3,
                    Unpooled.copiedBuffer(text.getBytes()), 0, true, newPromise());
                frameWriter.writeData(ctx(), 5,
                    Unpooled.copiedBuffer(text2.getBytes()), 0, true, newPromise());
                ctx().flush();
            }
        });
        awaitRequests();
        ArgumentCaptor<HttpObject> httpObjectCaptor = ArgumentCaptor.forClass(HttpObject.class);
        verify(messageObserver, times(4)).messageReceived(httpObjectCaptor.capture());

        List<HttpObject> capturedHttpObjects = httpObjectCaptor.getAllValues();
        assertEquals(request, capturedHttpObjects.get(0));
        assertEquals(content, capturedHttpObjects.get(1));
        assertEquals(request2, capturedHttpObjects.get(2));
        assertEquals(content2, capturedHttpObjects.get(3));
    }

    @Test
    public void clientRequestStreamDependency() throws Exception {
        serverLatch = new CountDownLatch(4);
        final HttpMessage request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                        HttpMethod.GET, "/some/path/resource", true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2HttpHeaders.Names.STREAM_ID, 3);
        final HttpMessage request2  = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                        HttpMethod.GET, "/some/path/resource2", true);
        HttpHeaders httpHeaders2 = request2.headers();
        httpHeaders2.set(Http2HttpHeaders.Names.STREAM_ID, 5);
        httpHeaders2.set(Http2HttpHeaders.Names.STREAM_DEPENDENCY_ID, 3);
        httpHeaders2.set(Http2HttpHeaders.Names.STREAM_EXCLUSIVE, true);
        httpHeaders2.set(Http2HttpHeaders.Names.STREAM_WEIGHT, 256);
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder()
            .method("GET").path("/some/path/resource").build();
        final Http2Headers http2Headers2 = new DefaultHttp2Headers.Builder()
            .method("GET").path("/some/path/resource2").build();
        final String text = "hello 1!**";
        final HttpContent content = new DefaultLastHttpContent(Unpooled.copiedBuffer(text.getBytes()),
                                                               true);
        final String text2 = "hello 2!><";
        final HttpContent content2 = new DefaultLastHttpContent(Unpooled.copiedBuffer(text2.getBytes()),
                                                                true);
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                frameWriter.writeHeaders(ctx(), 3, http2Headers, 0, false, newPromise());
                frameWriter.writeHeaders(ctx(), 5, http2Headers2, 0, false, newPromise());
                frameWriter.writePriority(ctx(), 5, 3, (short) 256, true, newPromise());
                frameWriter.writeData(ctx(), 3,
                    Unpooled.copiedBuffer(text.getBytes()), 0, true, newPromise());
                frameWriter.writeData(ctx(), 5,
                    Unpooled.copiedBuffer(text2.getBytes()), 0, true, newPromise());
                ctx().flush();
            }
        });
        awaitRequests();
        ArgumentCaptor<HttpObject> httpObjectCaptor = ArgumentCaptor.forClass(HttpObject.class);
        verify(messageObserver, times(4)).messageReceived(httpObjectCaptor.capture());

        List<HttpObject> capturedHttpObjects = httpObjectCaptor.getAllValues();
        assertEquals(request, capturedHttpObjects.get(0));
        assertEquals(content, capturedHttpObjects.get(1));
        assertEquals(request2, capturedHttpObjects.get(2));
        assertEquals(content2, capturedHttpObjects.get(3));
    }

    private void awaitRequests() throws Exception {
        serverLatch.await(2, SECONDS);
    }

    private ChannelHandlerContext ctx() {
        return clientChannel.pipeline().firstContext();
    }

    private ChannelPromise newPromise() {
        return ctx().newPromise();
    }

    private interface HttpResponseListener {
        void messageReceived(HttpObject obj);
    }

    private final class HttpResponseDelegator extends SimpleChannelInboundHandler<HttpObject> {
        private final HttpResponseListener listener;

        public HttpResponseDelegator(HttpResponseListener listener) {
            this.listener = listener;
        }

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            this.listener.messageReceived(msg);
            serverLatch.countDown();
        }
    }

    private final class FrameAdapter extends ByteToMessageDecoder {

        private final Http2FrameObserver observer;
        private final DefaultHttp2FrameReader reader;
        private final CountDownLatch requestLatch;

        FrameAdapter(Http2FrameObserver observer, CountDownLatch requestLatch) {
            this.observer = observer;
            reader = new DefaultHttp2FrameReader();
            this.requestLatch = requestLatch;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
                throws Exception {
            reader.readFrame(ctx, in, new Http2FrameObserver() {

                @Override
                public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                       int padding, boolean endOfStream)
                        throws Http2Exception {
                    observer.onDataRead(ctx, streamId, copy(data), padding, endOfStream);
                    requestLatch.countDown();
                }

                @Override
                public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                                          Http2Headers headers, int padding, boolean endStream)
                        throws Http2Exception {
                    observer.onHeadersRead(ctx, streamId, headers, padding, endStream);
                    requestLatch.countDown();
                }

                @Override
                public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                                          Http2Headers headers, int streamDependency, short weight,
                                          boolean exclusive, int padding, boolean endStream)
                        throws Http2Exception {
                    observer.onHeadersRead(ctx, streamId, headers, streamDependency, weight,
                            exclusive, padding, endStream);
                    requestLatch.countDown();
                }

                @Override
                public void onPriorityRead(ChannelHandlerContext ctx, int streamId,
                                           int streamDependency, short weight, boolean exclusive)
                        throws Http2Exception {
                    observer.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
                    requestLatch.countDown();
                }

                @Override
                public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                        throws Http2Exception {
                    observer.onRstStreamRead(ctx, streamId, errorCode);
                    requestLatch.countDown();
                }

                @Override
                public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
                    observer.onSettingsAckRead(ctx);
                    requestLatch.countDown();
                }

                @Override
                public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
                        throws Http2Exception {
                    observer.onSettingsRead(ctx, settings);
                    requestLatch.countDown();
                }

                @Override
                public void onPingRead(ChannelHandlerContext ctx, ByteBuf data)
                        throws Http2Exception {
                    observer.onPingRead(ctx, copy(data));
                    requestLatch.countDown();
                }

                @Override
                public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data)
                        throws Http2Exception {
                    observer.onPingAckRead(ctx, copy(data));
                    requestLatch.countDown();
                }

                @Override
                public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId,
                                              int promisedStreamId, Http2Headers headers, int padding)
                        throws Http2Exception {
                    observer.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
                    requestLatch.countDown();
                }

                @Override
                public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId,
                                         long errorCode, ByteBuf debugData) throws Http2Exception {
                    observer.onGoAwayRead(ctx, lastStreamId, errorCode, copy(debugData));
                    requestLatch.countDown();
                }

                @Override
                public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId,
                                               int windowSizeIncrement) throws Http2Exception {
                    observer.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
                    requestLatch.countDown();
                }

                @Override
                public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                        Http2Flags flags, ByteBuf payload) {
                    observer.onUnknownFrame(ctx, frameType, streamId, flags, payload);
                    requestLatch.countDown();
                }
            });
        }

        ByteBuf copy(ByteBuf buffer) {
            return Unpooled.copiedBuffer(buffer);
        }
    }
}
