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
import io.netty5.buffer.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalIoHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.FullHttpMessage;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpObject;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import io.netty5.util.AsciiString;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.handler.codec.http.HttpMethod.GET;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty5.handler.codec.http2.Http2CodecUtil.getEmbeddedHttp2Exception;
import static io.netty5.handler.codec.http2.Http2Exception.isStreamError;
import static io.netty5.handler.codec.http2.Http2TestUtil.of;
import static io.netty5.handler.codec.http2.Http2TestUtil.runInChannel;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testing the {@link InboundHttp2ToHttpAdapter} and base class {@link InboundHttp2ToHttpAdapter} for HTTP/2
 * frames into {@link HttpObject}s
 */
public class InboundHttp2ToHttpAdapterTest {
    private List<FullHttpMessage> capturedRequests;
    private List<FullHttpMessage> capturedResponses;

    private Listener<HttpObject> serverListener;
    private Listener<HttpObject> clientListener;
    private Listener<Http2Settings> settingsListener;

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

    private CountDownLatch clientHandlersAddedLatch;

    private CountDownLatch settingsLatch;
    private int maxContentLength;
    private HttpResponseDelegator serverDelegator;
    private HttpResponseDelegator clientDelegator;
    private HttpSettingsDelegator settingsDelegator;
    private Http2Exception clientException;

    @BeforeEach
    public void setup() throws Exception {
        serverListener = new Listener<>();
        clientListener = new Listener<>();
        settingsListener = new Listener<>();
    }

    @AfterEach
    public void tearDown() throws Exception {
        cleanupCapturedRequests();
        cleanupCapturedResponses();
        if (clientChannel != null) {
            clientChannel.close().asStage().sync();
            clientChannel = null;
        }
        if (serverChannel != null) {
            serverChannel.close().asStage().sync();
            serverChannel = null;
        }
        final Channel serverConnectedChannel = this.serverConnectedChannel;
        if (serverConnectedChannel != null) {
            serverConnectedChannel.close().asStage().sync();
            this.serverConnectedChannel = null;
        }
        Future<?> serverGroup = sb.config().group().shutdownGracefully(0, 5, SECONDS);
        Future<?> serverChildGroup = sb.config().childGroup().shutdownGracefully(0, 5, SECONDS);
        Future<?> clientGroup = cb.config().group().shutdownGracefully(0, 5, SECONDS);
        serverGroup.asStage().sync();
        serverChildGroup.asStage().sync();
        clientGroup.asStage().sync();
    }

    @Test
    public void clientRequestSingleHeaderNoDataFrames() throws Exception {
        boostrapEnv(1, 1, 1);
        try (FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, GET, "/some/path/resource2", preferredAllocator().allocate(0))) {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https");
            httpHeaders.set(HttpHeaderNames.HOST, "example.org");
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3");
            httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, "0");
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), "16");
            final Http2Headers http2Headers =
                    Http2Headers.newHeaders().method(new AsciiString("GET")).
                                scheme(new AsciiString("https")).authority(new AsciiString("example.org"))
                                .path(new AsciiString("/some/path/resource2"));
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, true);
                clientChannel.flush();
            });
            awaitRequests();
            capturedRequests = serverListener.getAll(FullHttpMessage.class);
            assertEquals(request, capturedRequests.get(0));
        }
    }

    @Test
    public void clientRequestSingleHeaderCookieSplitIntoMultipleEntries() throws Exception {
        boostrapEnv(1, 1, 1);
        try (FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, GET, "/some/path/resource2", preferredAllocator().allocate(0))) {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https");
            httpHeaders.set(HttpHeaderNames.HOST, "example.org");
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3");
            httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, "0");
            httpHeaders.set(HttpHeaderNames.COOKIE, "a=b; c=d; e=f");
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), "16");
            final Http2Headers http2Headers = Http2Headers.newHeaders().method(new AsciiString("GET")).
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
            capturedRequests = serverListener.getAll(FullHttpMessage.class);
            assertEquals(request, capturedRequests.get(0));
        }
    }

    @Test
    public void clientRequestSingleHeaderCookieSplitIntoMultipleEntries2() throws Exception {
        boostrapEnv(1, 1, 1);
        try (FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, GET, "/some/path/resource2", preferredAllocator().allocate(0))) {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https");
            httpHeaders.set(HttpHeaderNames.HOST, "example.org");
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3");
            httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, "0");
            httpHeaders.set(HttpHeaderNames.COOKIE, "a=b; c=d; e=f");
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), "16");
            final Http2Headers http2Headers = Http2Headers.newHeaders().method(new AsciiString("GET")).
                    scheme(new AsciiString("https")).authority(new AsciiString("example.org"))
                    .path(new AsciiString("/some/path/resource2"))
                    .add(HttpHeaderNames.COOKIE, "a=b; c=d")
                    .add(HttpHeaderNames.COOKIE, "e=f");
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, true);
                clientChannel.flush();
            });
            awaitRequests();
            capturedRequests = serverListener.getAll(FullHttpMessage.class);
            assertEquals(request, capturedRequests.get(0));
        }
    }

    @Disabled("This test is broken because it assumes that illegal headers will trip in InboundHttp2ToHttpAdapter, " +
              "and not in DefaultHttp2HeadersDecoder which is earlier in the pipeline.")
    @Test
    public void clientRequestSingleHeaderNonAsciiShouldThrow() throws Exception {
        boostrapEnv(1, 1, 1);
        final Http2Headers http2Headers = Http2Headers.newHeaders(false)
                .method(new AsciiString("GET"))
                .scheme(new AsciiString("https"))
                .authority(new AsciiString("example.org"))
                .path(new AsciiString("/some/path/resource2"))
                .add(new AsciiString("çã".getBytes(UTF_8)),
                        new AsciiString("Ãã".getBytes(UTF_8)));
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
        Buffer hello = preferredAllocator().allocate(16).writeCharSequence(text, UTF_8);
        try (FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/some/path/resource2", hello)) {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3");
            httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(text.length()));
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), "16");
            final Http2Headers http2Headers = Http2Headers.newHeaders().method(new AsciiString("GET")).path(
                    new AsciiString("/some/path/resource2"));
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false);
                clientHandler.encoder().writeData(ctxClient(), 3, hello.copy(),
                        0, true);
                clientChannel.flush();
            });
            awaitRequests();
            capturedRequests = serverListener.getAll(FullHttpMessage.class);
            assertEquals(request, capturedRequests.get(0));
        }
    }

    @Test
    public void clientRequestMultipleDataFrames() throws Exception {
        boostrapEnv(1, 1, 1);
        final String text = "hello world big time data!";
        try (Buffer content = preferredAllocator().allocate(32).writeCharSequence(text, UTF_8);
             FullHttpRequest request = new DefaultFullHttpRequest(
                     HTTP_1_1, GET, "/some/path/resource2", content.copy())) {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3");
            httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(text.length()));
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), "16");
            final Http2Headers http2Headers = Http2Headers.newHeaders().method(new AsciiString("GET")).path(
                    new AsciiString("/some/path/resource2"));
            final int midPoint = text.length() / 2;
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false);
                clientHandler.encoder().writeData(
                        ctxClient(), 3, content.copy(0, midPoint), 0, false);
                clientHandler.encoder().writeData(
                        ctxClient(), 3, content.copy(midPoint, text.length() - midPoint),
                        0, true);
                clientChannel.flush();
            });
            awaitRequests();
            capturedRequests = serverListener.getAll(FullHttpMessage.class);
            assertEquals(request, capturedRequests.get(0));
        }
    }

    @Test
    public void clientRequestMultipleEmptyDataFrames() throws Exception {
        boostrapEnv(1, 1, 1);
        final String text = "";
        final Buffer content = preferredAllocator().allocate(0);
        try (FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, GET, "/some/path/resource2", content)) {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3");
            httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(text.length()));
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), "16");
            final Http2Headers http2Headers = Http2Headers.newHeaders().method(new AsciiString("GET")).path(
                    new AsciiString("/some/path/resource2"));
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false);
                clientHandler.encoder().writeData(ctxClient(), 3, content.copy(), 0, false);
                clientHandler.encoder().writeData(ctxClient(), 3, content.copy(), 0, false);
                clientHandler.encoder().writeData(ctxClient(), 3, content.copy(), 0, true);
                clientChannel.flush();
            });
            awaitRequests();
            capturedRequests = serverListener.getAll(FullHttpMessage.class);
            assertEquals(request, capturedRequests.get(0));
        }
    }

    @Test
    public void clientRequestTrailingHeaders() throws Exception {
        boostrapEnv(1, 1, 1);
        final String text = "some data";
        Buffer content = preferredAllocator().allocate(16).writeCharSequence(text, UTF_8);
        try (FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, GET, "/some/path/resource2", content)) {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3");
            httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(text.length()));
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), "16");
            HttpHeaders trailingHeaders = request.trailingHeaders();
            trailingHeaders.set(of("Foo"), of("goo"));
            trailingHeaders.set(of("fOo2"), of("goo2"));
            trailingHeaders.add(of("foO2"), of("goo3"));
            final Http2Headers http2Headers = Http2Headers.newHeaders().method(new AsciiString("GET")).path(
                    new AsciiString("/some/path/resource2"));
            final Http2Headers http2Headers2 = Http2Headers.newHeaders()
                    .set(new AsciiString("foo"), new AsciiString("goo"))
                    .set(new AsciiString("foo2"), new AsciiString("goo2"))
                    .add(new AsciiString("foo2"), new AsciiString("goo3"));
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false);
                clientHandler.encoder().writeData(ctxClient(), 3, content.copy(), 0, false);
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers2, 0, true);
                clientChannel.flush();
            });
            awaitRequests();
            capturedRequests = serverListener.getAll(FullHttpMessage.class);
            assertEquals(request, capturedRequests.get(0));
        }
    }

    @Test
    public void clientRequestStreamDependencyInHttpMessageFlow() throws Exception {
        boostrapEnv(1, 2, 1);
        final String text = "hello world big time data!";
        Buffer content = preferredAllocator().allocate(32).writeCharSequence(text, UTF_8);

        final String text2 = "hello world big time data...number 2!!";
        Buffer content2 = preferredAllocator().allocate(64).writeCharSequence(text2, UTF_8);
        try (FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, HttpMethod.PUT, "/some/path/resource", content);
             FullHttpMessage<?> request2 = new DefaultFullHttpRequest(
                     HTTP_1_1, HttpMethod.PUT, "/some/path/resource2", content2)) {
            HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3");
            httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(text.length()));
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), "16");
            HttpHeaders httpHeaders2 = request2.headers();
            httpHeaders2.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "5");
            httpHeaders2.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_DEPENDENCY_ID.text(), "3");
            httpHeaders2.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), "123");
            httpHeaders2.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(text2.length()));
            final Http2Headers http2Headers = Http2Headers.newHeaders().method(new AsciiString("PUT")).path(
                    new AsciiString("/some/path/resource"));
            final Http2Headers http2Headers2 = Http2Headers.newHeaders().method(new AsciiString("PUT")).path(
                    new AsciiString("/some/path/resource2"));
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers, 0, false);
                clientHandler.encoder().writeHeaders(ctxClient(), 5, http2Headers2, 3, (short) 123, true, 0, false);
                clientChannel.flush(); // Headers are queued in the flow controller and so flush them.
                clientHandler.encoder().writeData(ctxClient(), 3, content.copy(), 0, true);
                clientHandler.encoder().writeData(ctxClient(), 5, content2.copy(), 0, true);
                clientChannel.flush();
            });
            awaitRequests();
            capturedRequests = serverListener.getAll(FullHttpMessage.class);
            assertThat(capturedRequests).containsExactly(request, request2);
        }
    }

    @Test
    public void serverRequestPushPromise() throws Exception {
        boostrapEnv(1, 1, 1);
        final String text = "hello world big time data!";
        Buffer content = preferredAllocator().allocate(32).writeCharSequence(text, UTF_8);
        final String text2 = "hello world smaller data?";
        Buffer content2 = preferredAllocator().allocate(32).writeCharSequence(text2, UTF_8);
        try (FullHttpMessage<?> response = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, content);
             FullHttpMessage<?> response2 = new DefaultFullHttpResponse(
                     HTTP_1_1, HttpResponseStatus.CREATED, content2);
             FullHttpMessage<?> request = new DefaultFullHttpRequest(
                     HTTP_1_1, GET, "/push/test", preferredAllocator().allocate(0))) {
            HttpHeaders httpHeaders = response.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3");
            httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(text.length()));
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), "16");
            HttpHeaders httpHeaders2 = response2.headers();
            httpHeaders2.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https");
            httpHeaders2.set(HttpHeaderNames.HOST, "example.org");
            httpHeaders2.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "5");
            httpHeaders2.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_PROMISE_ID.text(), "3");
            httpHeaders2.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(text2.length()));

            httpHeaders = request.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3");
            httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, "0");
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), "16");
            final Http2Headers http2Headers3 = Http2Headers.newHeaders().method(new AsciiString("GET"))
                    .path(new AsciiString("/push/test"));
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeHeaders(ctxClient(), 3, http2Headers3, 0, true);
                clientChannel.flush();
            });
            awaitRequests();
            capturedRequests = serverListener.getAll(FullHttpMessage.class);
            assertEquals(request, capturedRequests.get(0));

            final Http2Headers http2Headers = Http2Headers.newHeaders().status(new AsciiString("200"));
            // The PUSH_PROMISE frame includes a header block that contains a
            // complete set of request header fields that the server attributes to
            // the request.
            // https://tools.ietf.org/html/rfc7540#section-8.2.1
            // Therefore, we should consider the case where there is no Http response status.
            final Http2Headers http2Headers2 = Http2Headers.newHeaders()
                    .scheme(new AsciiString("https"))
                    .authority(new AsciiString("example.org"));
            runInChannel(serverConnectedChannel, () -> {
                serverHandler.encoder().writeHeaders(ctxServer(), 3, http2Headers, 0, false);
                serverHandler.encoder().writePushPromise(ctxServer(), 3, 2, http2Headers2, 0);
                serverHandler.encoder().writeData(ctxServer(), 3, content.copy(), 0, true);
                serverHandler.encoder().writeData(ctxServer(), 5, content2.copy(), 0, true);
                serverConnectedChannel.flush();
            });
            awaitResponses();
            capturedResponses = clientListener.getAll(FullHttpMessage.class);
            assertEquals(response, capturedResponses.get(0));
        }
    }

    @Test
    public void serverResponseHeaderInformational() throws Exception {
        boostrapEnv(1, 2, 1, 2, 1);
        final FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, HttpMethod.PUT, "/info/test", preferredAllocator().allocate(0));
        HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3");
        httpHeaders.set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);
        httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, "0");
        httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), "16");

        final Http2Headers http2Headers = Http2Headers.newHeaders().method(new AsciiString("PUT"))
                .path(new AsciiString("/info/test"))
                .set(new AsciiString(HttpHeaderNames.EXPECT.toString()),
                     new AsciiString(HttpHeaderValues.CONTINUE.toString()));
        final FullHttpMessage<?> response = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.CONTINUE, preferredAllocator().allocate(0));
        final String text = "a big payload";
        final Buffer payload = preferredAllocator().allocate(16).writeCharSequence(text, UTF_8);
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
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3");
            httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, "0");
            final Http2Headers http2HeadersResponse = Http2Headers.newHeaders().status(new AsciiString("100"));
            runInChannel(serverConnectedChannel, () -> {
                serverHandler.encoder().writeHeaders(ctxServer(), 3, http2HeadersResponse, 0, false);
                serverConnectedChannel.flush();
            });

            awaitResponses();
            httpHeaders = request2.headers();
            httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(text.length()));
            httpHeaders.remove(HttpHeaderNames.EXPECT);
            runInChannel(clientChannel, () -> {
                clientHandler.encoder().writeData(ctxClient(), 3, payload.copy(), 0, true);
                clientChannel.flush();
            });

            awaitRequests2();
            httpHeaders = response2.headers();
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3");
            httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, "0");
            httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), "16");

            final Http2Headers http2HeadersResponse2 = Http2Headers.newHeaders().status(new AsciiString("200"));
            runInChannel(serverConnectedChannel, () -> {
                serverHandler.encoder().writeHeaders(ctxServer(), 3, http2HeadersResponse2, 0, true);
                serverConnectedChannel.flush();
            });

            awaitResponses2();
            capturedRequests = serverListener.getAll(FullHttpMessage.class);
            assertEquals(2, capturedRequests.size());

            assertEquals(request, capturedRequests.get(0));
            assertEquals(request2, capturedRequests.get(1));

            capturedResponses = clientListener.getAll(FullHttpMessage.class);
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
        List<Http2Settings> settingsList = settingsListener.getAll(Http2Settings.class);
        assertThat(settingsList).hasSizeGreaterThanOrEqualTo(2);
        assertEquals(settings, settingsList.get(settingsList.size() - 1));
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
        clientHandlersAddedLatch = new CountDownLatch(1);

        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new MultithreadEventLoopGroup(LocalIoHandler.newFactory()));
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

        cb.group(new MultithreadEventLoopGroup(LocalIoHandler.newFactory()));
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
                p.addLast(new LoggingHandler(LogLevel.INFO));

                clientDelegator = new HttpResponseDelegator(clientListener, clientLatch, clientLatch2);
                p.addLast(clientDelegator);
                p.addLast(new ChannelHandler() {
                    @Override
                    public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        Http2Exception e = getEmbeddedHttp2Exception(cause);
                        if (e != null) {
                            clientException = e;
                            clientLatch.countDown();
                        } else {
                            ctx.fireChannelExceptionCaught(cause);
                        }
                    }
                });
                p.addLast(new ChannelHandler() {
                    @Override
                    public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == Http2ConnectionPrefaceAndSettingsFrameWrittenEvent.INSTANCE) {
                            prefaceWrittenLatch.countDown();
                            ctx.pipeline().remove(this);
                        }
                    }
                });
                p.addLast(new ChannelHandler() {
                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                        clientHandlersAddedLatch.countDown();
                    }
                });
            }
        });

        serverChannel = sb.bind(new LocalAddress(getClass())).asStage().get();

        clientChannel = cb.connect(serverChannel.localAddress()).asStage().get();
        assertTrue(prefaceWrittenLatch.await(5, SECONDS));
        assertTrue(serverChannelLatch.await(5, SECONDS));
        // Block until we are sure the handlers are added
        assertTrue(clientHandlersAddedLatch.await(5, SECONDS));
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

    private ChannelHandlerContext ctxServer() {
        return serverConnectedChannel.pipeline().firstContext();
    }

    private static final class Listener<T> {
        private final ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<>();

        void messageReceived(T obj) {
            queue.offer(obj);
        }

        <X> List<X> getAll(Class<X> cls) {
            List<X> list = new ArrayList<>();
            T obj;
            while ((obj = queue.poll()) != null) {
                if (cls.isInstance(obj)) {
                    list.add(cls.cast(obj));
                }
            }
            return list;
        }
    }

    private static final class HttpResponseDelegator extends SimpleChannelInboundHandler<HttpObject> {
        private final Listener<HttpObject> listener;
        private final CountDownLatch latch;
        private final CountDownLatch latch2;

        HttpResponseDelegator(Listener<HttpObject> listener, CountDownLatch latch, CountDownLatch latch2) {
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
        private final Listener<Http2Settings> listener;
        private final CountDownLatch latch;

        HttpSettingsDelegator(Listener<Http2Settings> listener, CountDownLatch latch) {
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
