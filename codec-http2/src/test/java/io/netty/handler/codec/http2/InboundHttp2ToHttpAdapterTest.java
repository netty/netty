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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2TestUtil.Http2Runnable;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static io.netty.handler.codec.http2.Http2CodecUtil.getEmbeddedHttp2Exception;
import static io.netty.handler.codec.http2.Http2Exception.isStreamError;
import static io.netty.handler.codec.http2.Http2TestUtil.of;
import static io.netty.handler.codec.http2.Http2TestUtil.runInChannel;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Testing the {@link InboundHttp2ToHttpAdapter} and base class {@link InboundHttp2ToHttpAdapter} for HTTP/2
 * frames into {@link HttpObject}s
 */
public class InboundHttp2ToHttpAdapterTest {
    private List<FullHttpMessage> capturedRequests;
    private List<FullHttpMessage> capturedResponses;

    @Mock
    private HttpResponseListener serverListener;

    @Mock
    private HttpResponseListener clientListener;

    @Mock
    private HttpSettingsListener settingsListener;

    private Http2ConnectionHandler serverHandler;
    private Http2ConnectionHandler clientHandler;
    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private volatile Channel serverConnectedChannel;
    private Channel clientChannel;
    private CountDownLatch serverLatch;
    private CountDownLatch clientLatch;
    private CountDownLatch serverLatch2;
    private CountDownLatch clientLatch2;
    private CountDownLatch settingsLatch;
    private int maxContentLength;
    private HttpResponseDelegator serverDelegator;
    private HttpResponseDelegator clientDelegator;
    private HttpSettingsDelegator settingsDelegator;
    private Http2Exception clientException;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void teardown() throws Exception {
        cleanupCapturedRequests();
        cleanupCapturedResponses();
        if (clientChannel != null) {
            clientChannel.close().syncUninterruptibly();
            clientChannel = null;
        }
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        final Channel serverConnectedChannel = this.serverConnectedChannel;
        if (serverConnectedChannel != null) {
            serverConnectedChannel.close().syncUninterruptibly();
            this.serverConnectedChannel = null;
        }
        Future<?> serverGroup = sb.config().group().shutdownGracefully(0, 5, SECONDS);
        Future<?> serverChildGroup = sb.config().childGroup().shutdownGracefully(0, 5, SECONDS);
        Future<?> clientGroup = cb.config().group().shutdownGracefully(0, 5, SECONDS);
        serverGroup.syncUninterruptibly();
        serverChildGroup.syncUninterruptibly();
        clientGroup.syncUninterruptibly();
    }

    @Test
    public void clientRequestSingleHeaderNoDataFrames() throws Exception {
        boostrapEnv(1, 1, 1);
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/some/path/resource2", true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https");
            httpHeaders.set(HttpHeaderNames.HOST, "example.org");
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("GET")).
                    scheme(new AsciiString("https")).authority(new AsciiString("example.org"))
                    .path(new AsciiString("/some/path/resource2"));
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, true, newPromiseClient());
                    clientChannel.flush();
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
    public void clientRequestSingleHeaderCookieSplitIntoMultipleEntries() throws Exception {
        boostrapEnv(1, 1, 1);
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/some/path/resource2", true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https");
            httpHeaders.set(HttpHeaderNames.HOST, "example.org");
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
            httpHeaders.set(HttpHeaderNames.COOKIE, "a=b; c=d; e=f");
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("GET")).
                    scheme(new AsciiString("https")).authority(new AsciiString("example.org"))
                    .path(new AsciiString("/some/path/resource2"))
                    .add(HttpHeaderNames.COOKIE, "a=b")
                    .add(HttpHeaderNames.COOKIE, "c=d")
                    .add(HttpHeaderNames.COOKIE, "e=f");
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, true, newPromiseClient());
                    clientChannel.flush();
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
    public void clientRequestSingleHeaderCookieSplitIntoMultipleEntries2() throws Exception {
        boostrapEnv(1, 1, 1);
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/some/path/resource2", true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https");
            httpHeaders.set(HttpHeaderNames.HOST, "example.org");
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
            httpHeaders.set(HttpHeaderNames.COOKIE, "a=b; c=d; e=f");
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("GET")).
                    scheme(new AsciiString("https")).authority(new AsciiString("example.org"))
                    .path(new AsciiString("/some/path/resource2"))
                    .add(HttpHeaderNames.COOKIE, "a=b; c=d")
                    .add(HttpHeaderNames.COOKIE, "e=f");
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, true, newPromiseClient());
                    clientChannel.flush();
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
    public void clientRequestSingleHeaderNonAsciiShouldThrow() throws Exception {
        boostrapEnv(1, 1, 1);
        final Http2Headers http2Headers = new DefaultHttp2Headers()
                .method(new AsciiString("GET"))
                .scheme(new AsciiString("https"))
                .authority(new AsciiString("example.org"))
                .path(new AsciiString("/some/path/resource2"))
                .add(new AsciiString("çã".getBytes(CharsetUtil.UTF_8)),
                        new AsciiString("Ãã".getBytes(CharsetUtil.UTF_8)));
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, true, newPromiseClient());
                clientChannel.flush();
            }
        });
        awaitResponses();
        assertTrue(isStreamError(clientException));
    }

    @Test
    public void clientRequestOneDataFrame() throws Exception {
        boostrapEnv(1, 1, 1);
        final String text = "hello world";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/some/path/resource2", content, true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, text.length());
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("GET")).path(
                    new AsciiString("/some/path/resource2"));
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    clientHandler.encoder().writeData(ctxClient(), 3, content.retainedDuplicate(), 0, true,
                                                      newPromiseClient());
                    clientChannel.flush();
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
        boostrapEnv(1, 1, 1);
        final String text = "hello world big time data!";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/some/path/resource2", content, true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, text.length());
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("GET")).path(
                    new AsciiString("/some/path/resource2"));
            final int midPoint = text.length() / 2;
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    clientHandler.encoder().writeData(
                            ctxClient(), 3, content.retainedSlice(0, midPoint), 0, false, newPromiseClient());
                    clientHandler.encoder().writeData(
                            ctxClient(), 3, content.retainedSlice(midPoint, text.length() - midPoint),
                            0, true, newPromiseClient());
                    clientChannel.flush();
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
        boostrapEnv(1, 1, 1);
        final String text = "";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/some/path/resource2", content, true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, text.length());
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("GET")).path(
                    new AsciiString("/some/path/resource2"));
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    clientHandler.encoder().writeData(ctxClient(), 3, content.retain(), 0, false, newPromiseClient());
                    clientHandler.encoder().writeData(ctxClient(), 3, content.retain(), 0, false, newPromiseClient());
                    clientHandler.encoder().writeData(ctxClient(), 3, content.retain(), 0, true, newPromiseClient());
                    clientChannel.flush();
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
        boostrapEnv(1, 1, 1);
        final String text = "some data";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/some/path/resource2", content, true);
        try {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, text.length());
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            HttpHeaders trailingHeaders = request.trailingHeaders();
            trailingHeaders.set(of("Foo"), of("goo"));
            trailingHeaders.set(of("fOo2"), of("goo2"));
            trailingHeaders.add(of("foO2"), of("goo3"));
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("GET")).path(
                    new AsciiString("/some/path/resource2"));
            final Http2Headers http2Headers2 = new DefaultHttp2Headers()
                    .set(new AsciiString("foo"), new AsciiString("goo"))
                    .set(new AsciiString("foo2"), new AsciiString("goo2"))
                    .add(new AsciiString("foo2"), new AsciiString("goo3"));
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    clientHandler.encoder().writeData(ctxClient(), 3, content.retainedDuplicate(), 0, false,
                                                      newPromiseClient());
                    clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers2, 0, true, newPromiseClient());
                    clientChannel.flush();
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
        boostrapEnv(1, 2, 1);
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
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, text.length());
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            HttpHeaders httpHeaders2 = request2.headers();
            httpHeaders2.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
            httpHeaders2.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_DEPENDENCY_ID.text(), 3);
            httpHeaders2.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 123);
            httpHeaders2.setInt(HttpHeaderNames.CONTENT_LENGTH, text2.length());
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("PUT")).path(
                    new AsciiString("/some/path/resource"));
            final Http2Headers http2Headers2 = new DefaultHttp2Headers().method(new AsciiString("PUT")).path(
                    new AsciiString("/some/path/resource2"));
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    clientHandler.encoder().writeHeaders(ctxClient(), 5, http2Headers2, 3, (short) 123, true, 0,
                            false, newPromiseClient());
                    clientChannel.flush(); // Headers are queued in the flow controller and so flush them.
                    clientHandler.encoder().writeData(ctxClient(), 3, content.retainedDuplicate(), 0, true,
                                                      newPromiseClient());
                    clientHandler.encoder().writeData(ctxClient(), 5, content2.retainedDuplicate(), 0, true,
                                                      newPromiseClient());
                    clientChannel.flush();
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
    public void serverRequestPushPromise() throws Exception {
        boostrapEnv(1, 1, 1);
        final String text = "hello world big time data!";
        final ByteBuf content = Unpooled.copiedBuffer(text.getBytes());
        final String text2 = "hello world smaller data?";
        final ByteBuf content2 = Unpooled.copiedBuffer(text2.getBytes());
        final FullHttpMessage response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                content, true);
        final FullHttpMessage response2 = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CREATED,
                content2, true);
        final FullHttpMessage request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/push/test",
                true);
        try {
            HttpHeaders httpHeaders = response.headers();
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, text.length());
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            HttpHeaders httpHeaders2 = response2.headers();
            httpHeaders2.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https");
            httpHeaders2.set(HttpHeaderNames.HOST, "example.org");
            httpHeaders2.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
            httpHeaders2.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_PROMISE_ID.text(), 3);
            httpHeaders2.setInt(HttpHeaderNames.CONTENT_LENGTH, text2.length());

            httpHeaders = request.headers();
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            final Http2Headers http2Headers3 = new DefaultHttp2Headers().method(new AsciiString("GET"))
                    .path(new AsciiString("/push/test"));
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers3, 0, true, newPromiseClient());
                    clientChannel.flush();
                }
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));

            final Http2Headers http2Headers = new DefaultHttp2Headers().status(new AsciiString("200"));
            // The PUSH_PROMISE frame includes a header block that contains a
            // complete set of request header fields that the server attributes to
            // the request.
            // https://tools.ietf.org/html/rfc7540#section-8.2.1
            // Therefore, we should consider the case where there is no Http response status.
            final Http2Headers http2Headers2 = new DefaultHttp2Headers()
                    .scheme(new AsciiString("https"))
                    .authority(new AsciiString("example.org"));
            runInChannel(serverConnectedChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    serverHandler.encoder().writeHeaders(ctxServer(), 3, http2Headers, 0, false, newPromiseServer());
                    serverHandler.encoder().writePushPromise(ctxServer(), 3, 2, http2Headers2, 0, newPromiseServer());
                    serverHandler.encoder().writeData(ctxServer(), 3, content.retainedDuplicate(), 0, true,
                                                      newPromiseServer());
                    serverHandler.encoder().writeData(ctxServer(), 5, content2.retainedDuplicate(), 0, true,
                                                      newPromiseServer());
                    serverConnectedChannel.flush();
                }
            });
            awaitResponses();
            ArgumentCaptor<FullHttpMessage> responseCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(clientListener).messageReceived(responseCaptor.capture());
            capturedResponses = responseCaptor.getAllValues();
            assertEquals(response, capturedResponses.get(0));
        } finally {
            request.release();
            response.release();
            response2.release();
        }
    }

    @Test
    public void serverResponseHeaderInformational() throws Exception {
        boostrapEnv(1, 2, 1, 2, 1);
        final FullHttpMessage request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/info/test",
                true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
        httpHeaders.set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);
        httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
        httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);

        final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("PUT"))
                .path(new AsciiString("/info/test"))
                .set(new AsciiString(HttpHeaderNames.EXPECT.toString()),
                     new AsciiString(HttpHeaderValues.CONTINUE.toString()));
        final FullHttpMessage response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE);
        final String text = "a big payload";
        final ByteBuf payload = Unpooled.copiedBuffer(text.getBytes());
        final FullHttpMessage request2 = request.replace(payload);
        final FullHttpMessage response2 = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        try {
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false, newPromiseClient());
                    clientChannel.flush();
                }
            });

            awaitRequests();
            httpHeaders = response.headers();
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
            final Http2Headers http2HeadersResponse = new DefaultHttp2Headers().status(new AsciiString("100"));
            runInChannel(serverConnectedChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    serverHandler.encoder().writeHeaders(ctxServer(), 3, http2HeadersResponse, 0, false,
                                                         newPromiseServer());
                    serverConnectedChannel.flush();
                }
            });

            awaitResponses();
            httpHeaders = request2.headers();
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, text.length());
            httpHeaders.remove(HttpHeaderNames.EXPECT);
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    clientHandler.encoder().writeData(ctxClient(), 3, payload.retainedDuplicate(), 0, true,
                                                      newPromiseClient());
                    clientChannel.flush();
                }
            });

            awaitRequests2();
            httpHeaders = response2.headers();
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);

            final Http2Headers http2HeadersResponse2 = new DefaultHttp2Headers().status(new AsciiString("200"));
            runInChannel(serverConnectedChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    serverHandler.encoder().writeHeaders(ctxServer(), 3, http2HeadersResponse2, 0, true,
                                                         newPromiseServer());
                    serverConnectedChannel.flush();
                }
            });

            awaitResponses2();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener, times(2)).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(2, capturedRequests.size());
            // We do not expect to have this header in the captured request so remove it now.
            assertNotNull(request.headers().remove("x-http2-stream-weight"));

            assertEquals(request, capturedRequests.get(0));
            assertEquals(request2, capturedRequests.get(1));

            ArgumentCaptor<FullHttpMessage> responseCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(clientListener, times(2)).messageReceived(responseCaptor.capture());
            capturedResponses = responseCaptor.getAllValues();
            assertEquals(2, capturedResponses.size());
            assertEquals(response, capturedResponses.get(0));
            assertEquals(response2, capturedResponses.get(1));
        } finally {
            request.release();
            request2.release();
            response.release();
            response2.release();
        }
    }

    @Test
    public void propagateSettings() throws Exception {
        boostrapEnv(1, 1, 2);
        final Http2Settings settings = new Http2Settings().pushEnabled(true);
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                clientHandler.encoder().writeSettings(ctxClient(), settings, newPromiseClient());
                clientChannel.flush();
            }
        });
        assertTrue(settingsLatch.await(5, SECONDS));
        ArgumentCaptor<Http2Settings> settingsCaptor = ArgumentCaptor.forClass(Http2Settings.class);
        verify(settingsListener, times(2)).messageReceived(settingsCaptor.capture());
        assertEquals(settings, settingsCaptor.getValue());
    }

    private void boostrapEnv(int clientLatchCount, int serverLatchCount, int settingsLatchCount)
                throws InterruptedException {
        boostrapEnv(clientLatchCount, clientLatchCount, serverLatchCount, serverLatchCount, settingsLatchCount);
    }

    private void boostrapEnv(int clientLatchCount, int clientLatchCount2, int serverLatchCount, int serverLatchCount2,
            int settingsLatchCount) throws InterruptedException {
        final CountDownLatch prefaceWrittenLatch = new CountDownLatch(1);
        clientDelegator = null;
        serverDelegator = null;
        serverConnectedChannel = null;
        maxContentLength = 1024;
        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        serverLatch = new CountDownLatch(serverLatchCount);
        clientLatch = new CountDownLatch(clientLatchCount);
        serverLatch2 = new CountDownLatch(serverLatchCount2);
        clientLatch2 = new CountDownLatch(clientLatchCount2);
        settingsLatch = new CountDownLatch(settingsLatchCount);

        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new DefaultEventLoopGroup());
        sb.channel(LocalServerChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                serverConnectedChannel = ch;
                ChannelPipeline p = ch.pipeline();
                Http2Connection connection = new DefaultHttp2Connection(true);

                serverHandler = new Http2ConnectionHandlerBuilder().frameListener(
                        new InboundHttp2ToHttpAdapterBuilder(connection)
                           .maxContentLength(maxContentLength)
                           .validateHttpHeaders(true)
                           .propagateSettings(true)
                           .build())
                   .connection(connection)
                   .gracefulShutdownTimeoutMillis(0)
                   .build();
                p.addLast(serverHandler);

                serverDelegator = new HttpResponseDelegator(serverListener, serverLatch, serverLatch2);
                p.addLast(serverDelegator);
                settingsDelegator = new HttpSettingsDelegator(settingsListener, settingsLatch);
                p.addLast(settingsDelegator);
                serverChannelLatch.countDown();
            }
        });

        cb.group(new DefaultEventLoopGroup());
        cb.channel(LocalChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                Http2Connection connection = new DefaultHttp2Connection(false);

                clientHandler = new Http2ConnectionHandlerBuilder().frameListener(
                        new InboundHttp2ToHttpAdapterBuilder(connection)
                           .maxContentLength(maxContentLength)
                           .build())
                   .connection(connection)
                   .gracefulShutdownTimeoutMillis(0)
                   .build();
                p.addLast(clientHandler);

                clientDelegator = new HttpResponseDelegator(clientListener, clientLatch, clientLatch2);
                p.addLast(clientDelegator);
                p.addLast(new ChannelHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        Http2Exception e = getEmbeddedHttp2Exception(cause);
                        if (e != null) {
                            clientException = e;
                            clientLatch.countDown();
                        } else {
                            super.exceptionCaught(ctx, cause);
                        }
                    }
                });
                p.addLast(new ChannelInboundHandlerAdapter() {
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == Http2ConnectionPrefaceAndSettingsFrameWrittenEvent.INSTANCE) {
                            prefaceWrittenLatch.countDown();
                            ctx.pipeline().remove(this);
                        }
                    }
                });
            }
        });

        serverChannel = sb.bind(new LocalAddress("InboundHttp2ToHttpAdapterTest")).sync().channel();

        ChannelFuture ccf = cb.connect(serverChannel.localAddress());
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
        assertTrue(prefaceWrittenLatch.await(5, SECONDS));
        assertTrue(serverChannelLatch.await(5, SECONDS));
    }

    private void cleanupCapturedRequests() {
        if (capturedRequests != null) {
            for (FullHttpMessage capturedRequest : capturedRequests) {
                capturedRequest.release();
            }
            capturedRequests = null;
        }
    }

    private void cleanupCapturedResponses() {
        if (capturedResponses != null) {
            for (FullHttpMessage capturedResponse : capturedResponses) {
                capturedResponse.release();
            }
            capturedResponses = null;
        }
    }

    private void awaitRequests() throws Exception {
        assertTrue(serverLatch.await(5, SECONDS));
    }

    private void awaitResponses() throws Exception {
        assertTrue(clientLatch.await(5, SECONDS));
    }

    private void awaitRequests2() throws Exception {
        assertTrue(serverLatch2.await(5, SECONDS));
    }

    private void awaitResponses2() throws Exception {
        assertTrue(clientLatch2.await(5, SECONDS));
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

    private interface HttpSettingsListener {
        void messageReceived(Http2Settings settings);
    }

    private static final class HttpResponseDelegator extends SimpleChannelInboundHandler<HttpObject> {
        private final HttpResponseListener listener;
        private final CountDownLatch latch;
        private final CountDownLatch latch2;

        HttpResponseDelegator(HttpResponseListener listener, CountDownLatch latch, CountDownLatch latch2) {
            super(false);
            this.listener = listener;
            this.latch = latch;
            this.latch2 = latch2;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            listener.messageReceived(msg);
            latch.countDown();
            latch2.countDown();
        }
    }

    private static final class HttpSettingsDelegator extends SimpleChannelInboundHandler<Http2Settings> {
        private final HttpSettingsListener listener;
        private final CountDownLatch latch;

        HttpSettingsDelegator(HttpSettingsListener listener, CountDownLatch latch) {
            super(false);
            this.listener = listener;
            this.latch = latch;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2Settings settings) throws Exception {
            listener.messageReceived(settings);
            latch.countDown();
        }
    }
}
