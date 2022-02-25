/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.http2;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.FullHttpMessage;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http.HttpHeaders;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpObject;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.util.AsciiString;
import io.netty5.util.CharsetUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.buffer.api.adaptor.ByteBufAdaptor.intoByteBuf;
import static io.netty5.handler.codec.http.HttpMethod.GET;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty5.handler.codec.http2.Http2CodecUtil.getEmbeddedHttp2Exception;
import static io.netty5.handler.codec.http2.Http2Exception.isStreamError;
import static io.netty5.handler.codec.http2.Http2TestUtil.of;
import static io.netty5.handler.codec.http2.Http2TestUtil.runInChannel;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
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
        try (FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, GET, "/some/path/resource2", preferredAllocator().allocate(0), true)) {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https");
            httpHeaders.set(HttpHeaderNames.HOST, "example.org");
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("GET")).
                    scheme(new AsciiString("https")).authority(new AsciiString("example.org"))
                    .path(new AsciiString("/some/path/resource2"));
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, true);
                clientChannel.flush();
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
        }
    }

    @Test
    public void clientRequestSingleHeaderCookieSplitIntoMultipleEntries() throws Exception {
        boostrapEnv(1, 1, 1);
        try (FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, GET, "/some/path/resource2", preferredAllocator().allocate(0), true)) {
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
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, true);
                clientChannel.flush();
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
        }
    }

    @Test
    public void clientRequestSingleHeaderCookieSplitIntoMultipleEntries2() throws Exception {
        boostrapEnv(1, 1, 1);
        try (FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, GET, "/some/path/resource2", preferredAllocator().allocate(0), true)) {
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
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, true);
                clientChannel.flush();
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
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
        runInChannel(clientChannel, () -> {
            clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, true);
            clientChannel.flush();
        });
        awaitResponses();
        assertTrue(isStreamError(clientException));
    }

    @Test
    public void clientRequestOneDataFrame() throws Exception {
        boostrapEnv(1, 1, 1);
        final String text = "hello world";
        Buffer hello = preferredAllocator().allocate(16).writeCharSequence(text, CharsetUtil.UTF_8);
        try (FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/some/path/resource2", hello, true)) {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, text.length());
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("GET")).path(
                    new AsciiString("/some/path/resource2"));
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false);
                clientHandler.encoder().writeData(ctxClient(), 3, intoByteBuf(hello.copy()),
                        0, true);
                clientChannel.flush();
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
        }
    }

    @Test
    public void clientRequestMultipleDataFrames() throws Exception {
        boostrapEnv(1, 1, 1);
        final String text = "hello world big time data!";
        try (Buffer content = preferredAllocator().allocate(32).writeCharSequence(text, CharsetUtil.UTF_8);
             FullHttpRequest request = new DefaultFullHttpRequest(
                     HTTP_1_1, GET, "/some/path/resource2", content.copy(), true)) {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, text.length());
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("GET")).path(
                    new AsciiString("/some/path/resource2"));
            final int midPoint = text.length() / 2;
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false);
                clientHandler.encoder().writeData(
                        ctxClient(), 3, intoByteBuf(content.copy(0, midPoint)), 0, false);
                clientHandler.encoder().writeData(
                        ctxClient(), 3, intoByteBuf(content.copy(midPoint, text.length() - midPoint)),
                        0, true);
                clientChannel.flush();
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
        }
    }

    @Test
    public void clientRequestMultipleEmptyDataFrames() throws Exception {
        boostrapEnv(1, 1, 1);
        final String text = "";
        final Buffer content = preferredAllocator().allocate(0);
        try (FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, GET, "/some/path/resource2", content, true)) {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, text.length());
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);
            final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("GET")).path(
                    new AsciiString("/some/path/resource2"));
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false);
                clientHandler.encoder().writeData(ctxClient(), 3, intoByteBuf(content.copy()), 0, false);
                clientHandler.encoder().writeData(ctxClient(), 3, intoByteBuf(content.copy()), 0, false);
                clientHandler.encoder().writeData(ctxClient(), 3, intoByteBuf(content.copy()), 0, true);
                clientChannel.flush();
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
        }
    }

    @Test
    public void clientRequestTrailingHeaders() throws Exception {
        boostrapEnv(1, 1, 1);
        final String text = "some data";
        Buffer content = preferredAllocator().allocate(16).writeCharSequence(text, CharsetUtil.UTF_8);
        try (FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, GET, "/some/path/resource2", content, true)) {
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
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false);
                clientHandler.encoder().writeData(ctxClient(), 3, intoByteBuf(content.copy()), 0, false);
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers2, 0, true);
                clientChannel.flush();
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
        }
    }

    @Test
    public void clientRequestStreamDependencyInHttpMessageFlow() throws Exception {
        boostrapEnv(1, 2, 1);
        final String text = "hello world big time data!";
        Buffer content = preferredAllocator().allocate(32).writeCharSequence(text, CharsetUtil.UTF_8);

        final String text2 = "hello world big time data...number 2!!";
        Buffer content2 = preferredAllocator().allocate(64).writeCharSequence(text2, CharsetUtil.UTF_8);
        try (FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, HttpMethod.PUT, "/some/path/resource", content, true);
             FullHttpMessage<?> request2 = new DefaultFullHttpRequest(
                     HTTP_1_1, HttpMethod.PUT, "/some/path/resource2", content2, true)) {
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
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false);
                clientHandler.encoder().writeHeaders(ctxClient(), 5, http2Headers2, 3, (short) 123, true, 0,
                        false);
                clientChannel.flush(); // Headers are queued in the flow controller and so flush them.
                clientHandler.encoder().writeData(ctxClient(), 3, intoByteBuf(content.copy()), 0, true);
                clientHandler.encoder().writeData(ctxClient(), 5, intoByteBuf(content2.copy()), 0, true);
                clientChannel.flush();
            });
            awaitRequests();
            ArgumentCaptor<FullHttpMessage> httpObjectCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener, times(2)).messageReceived(httpObjectCaptor.capture());
            capturedRequests = httpObjectCaptor.getAllValues();
            assertEquals(request, capturedRequests.get(0));
            assertEquals(request2, capturedRequests.get(1));
        }
    }

    @Test
    public void serverRequestPushPromise() throws Exception {
        boostrapEnv(1, 1, 1);
        final String text = "hello world big time data!";
        Buffer content = preferredAllocator().allocate(32).writeCharSequence(text, CharsetUtil.UTF_8);
        final String text2 = "hello world smaller data?";
        Buffer content2 = preferredAllocator().allocate(32).writeCharSequence(text2, CharsetUtil.UTF_8);
        try (FullHttpMessage<?> response = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, content, true);
             FullHttpMessage<?> response2 = new DefaultFullHttpResponse(
                     HTTP_1_1, HttpResponseStatus.CREATED, content2, true);
             FullHttpMessage<?> request = new DefaultFullHttpRequest(
                     HTTP_1_1, GET, "/push/test", preferredAllocator().allocate(0), true)) {
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
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers3, 0, true);
                clientChannel.flush();
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
            runInChannel(serverConnectedChannel, () -> {
                serverHandler.encoder().writeHeaders(ctxServer(), 3, http2Headers, 0, false);
                serverHandler.encoder().writePushPromise(ctxServer(), 3, 2, http2Headers2, 0);
                serverHandler.encoder().writeData(ctxServer(), 3, intoByteBuf(content.copy()), 0, true);
                serverHandler.encoder().writeData(ctxServer(), 5, intoByteBuf(content2.copy()), 0, true);
                serverConnectedChannel.flush();
            });
            awaitResponses();
            ArgumentCaptor<FullHttpMessage> responseCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(clientListener).messageReceived(responseCaptor.capture());
            capturedResponses = responseCaptor.getAllValues();
            assertEquals(response, capturedResponses.get(0));
        }
    }

    @Test
    public void serverResponseHeaderInformational() throws Exception {
        boostrapEnv(1, 2, 1, 2, 1);
        final FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, HttpMethod.PUT, "/info/test", preferredAllocator().allocate(0), true);
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
        httpHeaders.set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);
        httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
        httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);

        final Http2Headers http2Headers = new DefaultHttp2Headers().method(new AsciiString("PUT"))
                .path(new AsciiString("/info/test"))
                .set(new AsciiString(HttpHeaderNames.EXPECT.toString()),
                     new AsciiString(HttpHeaderValues.CONTINUE.toString()));
        final FullHttpMessage<?> response = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.CONTINUE, preferredAllocator().allocate(0));
        final String text = "a big payload";
        final Buffer payload = preferredAllocator().allocate(16).writeCharSequence(text, StandardCharsets.UTF_8);
        final FullHttpMessage<?> request2 = new DefaultFullHttpRequest(request.protocolVersion(), request.method(),
                request.uri(), payload, request.headers(), request.trailingHeaders());
        final FullHttpMessage<?> response2 = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().allocate(0));

        try {
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false);
                clientChannel.flush();
            });

            awaitRequests();
            httpHeaders = response.headers();
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
            final Http2Headers http2HeadersResponse = new DefaultHttp2Headers().status(new AsciiString("100"));
            runInChannel(serverConnectedChannel, () -> {
                serverHandler.encoder().writeHeaders(ctxServer(), 3, http2HeadersResponse, 0, false);
                serverConnectedChannel.flush();
            });

            awaitResponses();
            httpHeaders = request2.headers();
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, text.length());
            httpHeaders.remove(HttpHeaderNames.EXPECT);
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeData(ctxClient(), 3, intoByteBuf(payload.copy()), 0, true);
                clientChannel.flush();
            });

            awaitRequests2();
            httpHeaders = response2.headers();
            httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
            httpHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
            httpHeaders.setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), (short) 16);

            final Http2Headers http2HeadersResponse2 = new DefaultHttp2Headers().status(new AsciiString("200"));
            runInChannel(serverConnectedChannel, () -> {
                serverHandler.encoder().writeHeaders(ctxServer(), 3, http2HeadersResponse2, 0, true);
                serverConnectedChannel.flush();
            });

            awaitResponses2();
            ArgumentCaptor<FullHttpMessage> requestCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(serverListener, times(2)).messageReceived(requestCaptor.capture());
            capturedRequests = requestCaptor.getAllValues();
            assertEquals(2, capturedRequests.size());

            assertEquals(request, capturedRequests.get(0));
            assertEquals(request2, capturedRequests.get(1));

            ArgumentCaptor<FullHttpMessage> responseCaptor = ArgumentCaptor.forClass(FullHttpMessage.class);
            verify(clientListener, times(2)).messageReceived(responseCaptor.capture());
            capturedResponses = responseCaptor.getAllValues();
            assertEquals(2, capturedResponses.size());
            assertEquals(response, capturedResponses.get(0));
            assertEquals(response2, capturedResponses.get(1));
        } finally {
            request.close();
            request2.close();
            response.close();
            response2.close();
        }
    }

    @Test
    public void propagateSettings() throws Exception {
        boostrapEnv(1, 1, 2);
        final Http2Settings settings = new Http2Settings().pushEnabled(true);
        runInChannel(clientChannel, () -> {
            clientHandler.encoder().writeSettings(ctxClient(), settings);
            clientChannel.flush();
        });
        assertTrue(settingsLatch.await(5, SECONDS));
        ArgumentCaptor<Http2Settings> settingsCaptor = ArgumentCaptor.forClass(Http2Settings.class);
        verify(settingsListener, times(2)).messageReceived(settingsCaptor.capture());
        assertEquals(settings, settingsCaptor.getValue());
    }

    private void boostrapEnv(int clientLatchCount, int serverLatchCount, int settingsLatchCount) throws Exception {
        boostrapEnv(clientLatchCount, clientLatchCount, serverLatchCount, serverLatchCount, settingsLatchCount);
    }

    private void boostrapEnv(int clientLatchCount, int clientLatchCount2, int serverLatchCount, int serverLatchCount2,
            int settingsLatchCount) throws Exception {
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

        sb.group(new MultithreadEventLoopGroup(LocalHandler.newFactory()));
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

        cb.group(new MultithreadEventLoopGroup(LocalHandler.newFactory()));
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
                p.addLast(new ChannelHandler() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        Http2Exception e = getEmbeddedHttp2Exception(cause);
                        if (e != null) {
                            clientException = e;
                            clientLatch.countDown();
                        } else {
                            ctx.fireExceptionCaught(cause);
                        }
                    }
                });
                p.addLast(new ChannelHandler() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == Http2ConnectionPrefaceAndSettingsFrameWrittenEvent.INSTANCE) {
                            prefaceWrittenLatch.countDown();
                            ctx.pipeline().remove(this);
                        }
                    }
                });
            }
        });

        serverChannel = sb.bind(new LocalAddress("InboundHttp2ToHttpAdapterTest")).get();

        clientChannel = cb.connect(serverChannel.localAddress()).get();
        assertTrue(prefaceWrittenLatch.await(5, SECONDS));
        assertTrue(serverChannelLatch.await(5, SECONDS));
    }

    private void cleanupCapturedRequests() {
        if (capturedRequests != null) {
            for (FullHttpMessage<?> capturedRequest : capturedRequests) {
                capturedRequest.close();
            }
            capturedRequests = null;
        }
    }

    private void cleanupCapturedResponses() {
        if (capturedResponses != null) {
            for (FullHttpMessage<?> capturedResponse : capturedResponses) {
                capturedResponse.close();
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

    private Promise<Void> newPromiseClient() {
        return ctxClient().newPromise();
    }

    private ChannelHandlerContext ctxServer() {
        return serverConnectedChannel.pipeline().firstContext();
    }

    private Promise<Void> newPromiseServer() {
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
        protected void messageReceived(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
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
        protected void messageReceived(ChannelHandlerContext ctx, Http2Settings settings) throws Exception {
            listener.messageReceived(settings);
            latch.countDown();
        }
    }
}
