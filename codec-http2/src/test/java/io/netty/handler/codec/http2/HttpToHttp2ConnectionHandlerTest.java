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
package io.netty.handler.codec.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.Http2TestUtil.FrameCountDown;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static io.netty.handler.codec.http.HttpMethod.CONNECT;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.OPTIONS;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.http2.Http2TestUtil.of;
import static io.netty.util.CharsetUtil.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Testing the {@link HttpToHttp2ConnectionHandler} for {@link FullHttpRequest} objects into HTTP/2 frames
 */
public class HttpToHttp2ConnectionHandlerTest {
    private static final int WAIT_TIME_SECONDS = 5;

    @Mock
    private Http2FrameListener clientListener;

    @Mock
    private Http2FrameListener serverListener;

    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private volatile Channel serverConnectedChannel;
    private Channel clientChannel;
    private CountDownLatch requestLatch;
    private CountDownLatch serverSettingsAckLatch;
    private CountDownLatch trailersLatch;
    private FrameCountDown serverFrameCountDown;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void teardown() throws Exception {
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
    public void testHeadersOnlyRequest() throws Exception {
        bootstrapEnv(2, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET,
                "http://my-user_name@www.example.org:5555/example");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
        httpHeaders.set(HttpHeaderNames.HOST, "my-user_name@www.example.org:5555");
        httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "http");
        httpHeaders.add(of("foo"), of("goo"));
        httpHeaders.add(of("foo"), of("goo2"));
        httpHeaders.add(of("foo2"), of("goo2"));
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("GET")).path(new AsciiString("/example"))
                .authority(new AsciiString("www.example.org:5555")).scheme(new AsciiString("http"))
                .add(new AsciiString("foo"), new AsciiString("goo"))
                .add(new AsciiString("foo"), new AsciiString("goo2"))
                .add(new AsciiString("foo2"), new AsciiString("goo2"));

        ChannelPromise writePromise = newPromise();
        verifyHeadersOnly(http2Headers, writePromise, clientChannel.writeAndFlush(request, writePromise));
    }

    @Test
    public void testHttpScheme() throws Exception {
        bootstrapEnv(2, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET,
                "http://my-user_name@www.example.org:5555/example");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
        httpHeaders.set(HttpHeaderNames.HOST, "my-user_name@www.example.org:5555");
        httpHeaders.add(of("foo"), of("goo"));
        httpHeaders.add(of("foo"), of("goo2"));
        httpHeaders.add(of("foo2"), of("goo2"));
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("GET")).path(new AsciiString("/example"))
                        .authority(new AsciiString("www.example.org:5555")).scheme(new AsciiString("http"))
                        .scheme(new AsciiString("http"))
                        .add(new AsciiString("foo"), new AsciiString("goo"))
                        .add(new AsciiString("foo"), new AsciiString("goo2"))
                        .add(new AsciiString("foo2"), new AsciiString("goo2"));

        ChannelPromise writePromise = newPromise();
        verifyHeadersOnly(http2Headers, writePromise, clientChannel.writeAndFlush(request, writePromise));
    }

    @Test
    public void testMultipleCookieEntriesAreCombined() throws Exception {
        bootstrapEnv(2, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET,
                "http://my-user_name@www.example.org:5555/example");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
        httpHeaders.set(HttpHeaderNames.HOST, "my-user_name@www.example.org:5555");
        httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "http");
        httpHeaders.set(HttpHeaderNames.COOKIE, "a=b; c=d; e=f");
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("GET")).path(new AsciiString("/example"))
                .authority(new AsciiString("www.example.org:5555")).scheme(new AsciiString("http"))
                .add(HttpHeaderNames.COOKIE, "a=b")
                .add(HttpHeaderNames.COOKIE, "c=d")
                .add(HttpHeaderNames.COOKIE, "e=f");

        ChannelPromise writePromise = newPromise();
        verifyHeadersOnly(http2Headers, writePromise, clientChannel.writeAndFlush(request, writePromise));
    }

    @Test
    public void testOriginFormRequestTargetHandled() throws Exception {
        bootstrapEnv(2, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/where?q=now&f=then#section1");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
        httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "http");
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("GET"))
                .path(new AsciiString("/where?q=now&f=then#section1"))
                .scheme(new AsciiString("http"));

        ChannelPromise writePromise = newPromise();
        verifyHeadersOnly(http2Headers, writePromise, clientChannel.writeAndFlush(request, writePromise));
    }

    @Test
    public void testOriginFormRequestTargetHandledFromUrlencodedUri() throws Exception {
        bootstrapEnv(2, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, GET, "/where%2B0?q=now%2B0&f=then%2B0#section1%2B0");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
        httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "http");
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("GET"))
                                         .path(new AsciiString("/where%2B0?q=now%2B0&f=then%2B0#section1%2B0"))
                                         .scheme(new AsciiString("http"));

        ChannelPromise writePromise = newPromise();
        verifyHeadersOnly(http2Headers, writePromise, clientChannel.writeAndFlush(request, writePromise));
    }

    @Test
    public void testAbsoluteFormRequestTargetHandledFromHeaders() throws Exception {
        bootstrapEnv(2, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/pub/WWW/TheProject.html");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
        httpHeaders.set(HttpHeaderNames.HOST, "foouser@www.example.org:5555");
        httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.PATH.text(), "ignored_path");
        httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https");
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("GET"))
                .path(new AsciiString("/pub/WWW/TheProject.html"))
                .authority(new AsciiString("www.example.org:5555")).scheme(new AsciiString("https"));

        ChannelPromise writePromise = newPromise();
        verifyHeadersOnly(http2Headers, writePromise, clientChannel.writeAndFlush(request, writePromise));
    }

    @Test
    public void testAbsoluteFormRequestTargetHandledFromRequestTargetUri() throws Exception {
        bootstrapEnv(2, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET,
                "http://foouser@www.example.org:5555/pub/WWW/TheProject.html");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("GET"))
                .path(new AsciiString("/pub/WWW/TheProject.html"))
                .authority(new AsciiString("www.example.org:5555")).scheme(new AsciiString("http"));

        ChannelPromise writePromise = newPromise();
        verifyHeadersOnly(http2Headers, writePromise, clientChannel.writeAndFlush(request, writePromise));
    }

    @Test
    public void testAuthorityFormRequestTargetHandled() throws Exception {
        bootstrapEnv(2, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, CONNECT, "http://www.example.com:80");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("CONNECT")).path(new AsciiString("/"))
                .scheme(new AsciiString("http")).authority(new AsciiString("www.example.com:80"));

        ChannelPromise writePromise = newPromise();
        verifyHeadersOnly(http2Headers, writePromise, clientChannel.writeAndFlush(request, writePromise));
    }

    @Test
    public void testAsterikFormRequestTargetHandled() throws Exception {
        bootstrapEnv(2, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, OPTIONS, "*");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
        httpHeaders.set(HttpHeaderNames.HOST, "www.example.com:80");
        httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "http");
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("OPTIONS")).path(new AsciiString("*"))
                .scheme(new AsciiString("http")).authority(new AsciiString("www.example.com:80"));

        ChannelPromise writePromise = newPromise();
        verifyHeadersOnly(http2Headers, writePromise, clientChannel.writeAndFlush(request, writePromise));
    }

    @Test
    public void testHostIPv6FormRequestTargetHandled() throws Exception {
        // Valid according to
        // https://tools.ietf.org/html/rfc7230#section-2.7.1 -> https://tools.ietf.org/html/rfc3986#section-3.2.2
        bootstrapEnv(2, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
        httpHeaders.set(HttpHeaderNames.HOST, "[::1]:80");
        httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "http");
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("GET")).path(new AsciiString("/"))
                .scheme(new AsciiString("http")).authority(new AsciiString("[::1]:80"));

        ChannelPromise writePromise = newPromise();
        verifyHeadersOnly(http2Headers, writePromise, clientChannel.writeAndFlush(request, writePromise));
    }

    @Test
    public void testHostFormRequestTargetHandled() throws Exception {
        bootstrapEnv(2, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
        httpHeaders.set(HttpHeaderNames.HOST, "localhost:80");
        httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "http");
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("GET")).path(new AsciiString("/"))
                .scheme(new AsciiString("http")).authority(new AsciiString("localhost:80"));

        ChannelPromise writePromise = newPromise();
        verifyHeadersOnly(http2Headers, writePromise, clientChannel.writeAndFlush(request, writePromise));
    }

    @Test
    public void testHostIPv4FormRequestTargetHandled() throws Exception {
        bootstrapEnv(2, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
        httpHeaders.set(HttpHeaderNames.HOST, "1.2.3.4:80");
        httpHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "http");
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("GET")).path(new AsciiString("/"))
                .scheme(new AsciiString("http")).authority(new AsciiString("1.2.3.4:80"));

        ChannelPromise writePromise = newPromise();
        verifyHeadersOnly(http2Headers, writePromise, clientChannel.writeAndFlush(request, writePromise));
    }

    @Test
    public void testNoSchemeRequestTargetHandled() throws Exception {
        bootstrapEnv(2, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5);
        httpHeaders.set(HttpHeaderNames.HOST, "localhost");
        ChannelPromise writePromise = newPromise();
        ChannelFuture writeFuture = clientChannel.writeAndFlush(request, writePromise);

        assertTrue(writePromise.awaitUninterruptibly(WAIT_TIME_SECONDS, SECONDS));
        assertTrue(writePromise.isDone());
        assertFalse(writePromise.isSuccess());
        assertTrue(writeFuture.isDone());
        assertFalse(writeFuture.isSuccess());
    }

    @Test
    public void testRequestWithBody() throws Exception {
        final String text = "foooooogoooo";
        final List<String> receivedBuffers = Collections.synchronizedList(new ArrayList<String>());
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock in) throws Throwable {
                receivedBuffers.add(((ByteBuf) in.getArguments()[2]).toString(UTF_8));
                return null;
            }
        }).when(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3),
                any(ByteBuf.class), eq(0), eq(true));
        bootstrapEnv(3, 1, 0);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, POST,
                "http://your_user-name123@www.example.org:5555/example",
                Unpooled.copiedBuffer(text, UTF_8));
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(HttpHeaderNames.HOST, "www.example-origin.org:5555");
        httpHeaders.add(of("foo"), of("goo"));
        httpHeaders.add(of("foo"), of("goo2"));
        httpHeaders.add(of("foo2"), of("goo2"));
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("POST")).path(new AsciiString("/example"))
                .authority(new AsciiString("www.example-origin.org:5555")).scheme(new AsciiString("http"))
                .add(new AsciiString("foo"), new AsciiString("goo"))
                .add(new AsciiString("foo"), new AsciiString("goo2"))
                .add(new AsciiString("foo2"), new AsciiString("goo2"));
        ChannelPromise writePromise = newPromise();
        ChannelFuture writeFuture = clientChannel.writeAndFlush(request, writePromise);

        assertTrue(writePromise.awaitUninterruptibly(WAIT_TIME_SECONDS, SECONDS));
        assertTrue(writePromise.isSuccess());
        assertTrue(writeFuture.awaitUninterruptibly(WAIT_TIME_SECONDS, SECONDS));
        assertTrue(writeFuture.isSuccess());
        awaitRequests();
        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(http2Headers), eq(0),
                anyShort(), anyBoolean(), eq(0), eq(false));
        verify(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3), any(ByteBuf.class), eq(0),
                eq(true));
        assertEquals(1, receivedBuffers.size());
        assertEquals(text, receivedBuffers.get(0));
    }

    @Test
    public void testRequestWithBodyAndTrailingHeaders() throws Exception {
        final String text = "foooooogoooo";
        final List<String> receivedBuffers = Collections.synchronizedList(new ArrayList<String>());
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock in) throws Throwable {
                receivedBuffers.add(((ByteBuf) in.getArguments()[2]).toString(UTF_8));
                return null;
            }
        }).when(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3),
                any(ByteBuf.class), eq(0), eq(false));
        bootstrapEnv(4, 1, 1);
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, POST,
                "http://your_user-name123@www.example.org:5555/example",
                Unpooled.copiedBuffer(text, UTF_8));
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(HttpHeaderNames.HOST, "www.example.org:5555");
        httpHeaders.add(of("foo"), of("goo"));
        httpHeaders.add(of("foo"), of("goo2"));
        httpHeaders.add(of("foo2"), of("goo2"));
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("POST")).path(new AsciiString("/example"))
                        .authority(new AsciiString("www.example.org:5555")).scheme(new AsciiString("http"))
                        .add(new AsciiString("foo"), new AsciiString("goo"))
                        .add(new AsciiString("foo"), new AsciiString("goo2"))
                        .add(new AsciiString("foo2"), new AsciiString("goo2"));

        request.trailingHeaders().add(of("trailing"), of("bar"));

        final Http2Headers http2TrailingHeaders = new DefaultHttp2Headers()
                .add(new AsciiString("trailing"), new AsciiString("bar"));

        ChannelPromise writePromise = newPromise();
        ChannelFuture writeFuture = clientChannel.writeAndFlush(request, writePromise);

        assertTrue(writePromise.awaitUninterruptibly(WAIT_TIME_SECONDS, SECONDS));
        assertTrue(writePromise.isSuccess());
        assertTrue(writeFuture.awaitUninterruptibly(WAIT_TIME_SECONDS, SECONDS));
        assertTrue(writeFuture.isSuccess());
        awaitRequests();
        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(http2Headers), eq(0),
                anyShort(), anyBoolean(), eq(0), eq(false));
        verify(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3), any(ByteBuf.class), eq(0),
                eq(false));
        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(http2TrailingHeaders), eq(0),
                anyShort(), anyBoolean(), eq(0), eq(true));
        assertEquals(1, receivedBuffers.size());
        assertEquals(text, receivedBuffers.get(0));
    }

    @Test
    public void testChunkedRequestWithBodyAndTrailingHeaders() throws Exception {
        final String text = "foooooo";
        final String text2 = "goooo";
        final List<String> receivedBuffers = Collections.synchronizedList(new ArrayList<String>());
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock in) throws Throwable {
                receivedBuffers.add(((ByteBuf) in.getArguments()[2]).toString(UTF_8));
                return null;
            }
        }).when(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3),
                any(ByteBuf.class), eq(0), eq(false));
        bootstrapEnv(4, 1, 1);
        final HttpRequest request = new DefaultHttpRequest(HTTP_1_1, POST,
                "http://your_user-name123@www.example.org:5555/example");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(HttpHeaderNames.HOST, "www.example.org:5555");
        httpHeaders.add(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
        httpHeaders.add(of("foo"), of("goo"));
        httpHeaders.add(of("foo"), of("goo2"));
        httpHeaders.add(of("foo2"), of("goo2"));
        final Http2Headers http2Headers =
                new DefaultHttp2Headers().method(new AsciiString("POST")).path(new AsciiString("/example"))
                        .authority(new AsciiString("www.example.org:5555")).scheme(new AsciiString("http"))
                        .add(new AsciiString("foo"), new AsciiString("goo"))
                        .add(new AsciiString("foo"), new AsciiString("goo2"))
                        .add(new AsciiString("foo2"), new AsciiString("goo2"));

        final DefaultHttpContent httpContent = new DefaultHttpContent(Unpooled.copiedBuffer(text, UTF_8));
        final LastHttpContent lastHttpContent = new DefaultLastHttpContent(Unpooled.copiedBuffer(text2, UTF_8));

        lastHttpContent.trailingHeaders().add(of("trailing"), of("bar"));

        final Http2Headers http2TrailingHeaders = new DefaultHttp2Headers()
                .add(new AsciiString("trailing"), new AsciiString("bar"));

        ChannelPromise writePromise = newPromise();
        ChannelFuture writeFuture = clientChannel.write(request, writePromise);
        ChannelPromise contentPromise = newPromise();
        ChannelFuture contentFuture = clientChannel.write(httpContent, contentPromise);
        ChannelPromise lastContentPromise = newPromise();
        ChannelFuture lastContentFuture = clientChannel.write(lastHttpContent, lastContentPromise);

        clientChannel.flush();

        assertTrue(writePromise.awaitUninterruptibly(WAIT_TIME_SECONDS, SECONDS));
        assertTrue(writePromise.isSuccess());
        assertTrue(writeFuture.awaitUninterruptibly(WAIT_TIME_SECONDS, SECONDS));
        assertTrue(writeFuture.isSuccess());

        assertTrue(contentPromise.awaitUninterruptibly(WAIT_TIME_SECONDS, SECONDS));
        assertTrue(contentPromise.isSuccess());
        assertTrue(contentFuture.awaitUninterruptibly(WAIT_TIME_SECONDS, SECONDS));
        assertTrue(contentFuture.isSuccess());

        assertTrue(lastContentPromise.awaitUninterruptibly(WAIT_TIME_SECONDS, SECONDS));
        assertTrue(lastContentPromise.isSuccess());
        assertTrue(lastContentFuture.awaitUninterruptibly(WAIT_TIME_SECONDS, SECONDS));
        assertTrue(lastContentFuture.isSuccess());

        awaitRequests();
        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(http2Headers), eq(0),
                anyShort(), anyBoolean(), eq(0), eq(false));
        verify(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3), any(ByteBuf.class), eq(0),
                eq(false));
        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(http2TrailingHeaders), eq(0),
                anyShort(), anyBoolean(), eq(0), eq(true));
        assertEquals(1, receivedBuffers.size());
        assertEquals(text + text2, receivedBuffers.get(0));
    }

    private void bootstrapEnv(int requestCountDown, int serverSettingsAckCount, int trailersCount) throws Exception {
        final CountDownLatch prefaceWrittenLatch = new CountDownLatch(1);
        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        requestLatch = new CountDownLatch(requestCountDown);
        serverSettingsAckLatch = new CountDownLatch(serverSettingsAckCount);
        trailersLatch = trailersCount == 0 ? null : new CountDownLatch(trailersCount);

        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new DefaultEventLoopGroup());
        sb.channel(LocalServerChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                serverConnectedChannel = ch;
                ChannelPipeline p = ch.pipeline();
                serverFrameCountDown =
                        new FrameCountDown(serverListener, serverSettingsAckLatch, requestLatch, null, trailersLatch);
                p.addLast(new HttpToHttp2ConnectionHandlerBuilder()
                           .server(true)
                           .frameListener(serverFrameCountDown)
                           .httpScheme(HttpScheme.HTTP)
                           .build());
                serverChannelLatch.countDown();
            }
        });

        cb.group(new DefaultEventLoopGroup());
        cb.channel(LocalChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                HttpToHttp2ConnectionHandler handler = new HttpToHttp2ConnectionHandlerBuilder()
                        .server(false)
                        .frameListener(clientListener)
                        .gracefulShutdownTimeoutMillis(0)
                        .build();
                p.addLast(handler);
                p.addLast(new ChannelInboundHandlerAdapter() {
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

        serverChannel = sb.bind(new LocalAddress("HttpToHttp2ConnectionHandlerTest")).sync().channel();

        ChannelFuture ccf = cb.connect(serverChannel.localAddress());
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
        assertTrue(prefaceWrittenLatch.await(5, SECONDS));
        assertTrue(serverChannelLatch.await(WAIT_TIME_SECONDS, SECONDS));
    }

    private void verifyHeadersOnly(Http2Headers expected, ChannelPromise writePromise, ChannelFuture writeFuture)
            throws Exception {
        assertTrue(writePromise.awaitUninterruptibly(WAIT_TIME_SECONDS, SECONDS));
        assertTrue(writePromise.isSuccess());
        assertTrue(writeFuture.awaitUninterruptibly(WAIT_TIME_SECONDS, SECONDS));
        assertTrue(writeFuture.isSuccess());
        awaitRequests();
        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(5),
                eq(expected), eq(0), anyShort(), anyBoolean(), eq(0), eq(true));
        verify(serverListener, never()).onDataRead(any(ChannelHandlerContext.class), anyInt(),
                any(ByteBuf.class), anyInt(), anyBoolean());
    }

    private void awaitRequests() throws Exception {
        assertTrue(requestLatch.await(WAIT_TIME_SECONDS, SECONDS));
        if (trailersLatch != null) {
            assertTrue(trailersLatch.await(WAIT_TIME_SECONDS, SECONDS));
        }
        assertTrue(serverSettingsAckLatch.await(WAIT_TIME_SECONDS, SECONDS));
    }

    private ChannelHandlerContext ctx() {
        return clientChannel.pipeline().firstContext();
    }

    private ChannelPromise newPromise() {
        return ctx().newPromise();
    }
}
