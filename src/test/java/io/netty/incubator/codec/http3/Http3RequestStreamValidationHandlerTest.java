/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.incubator.codec.http3;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.incubator.codec.http3.Http3ErrorCode.H3_FRAME_UNEXPECTED;
import static io.netty.incubator.codec.http3.Http3RequestStreamValidationHandler.newClientValidator;
import static io.netty.incubator.codec.http3.Http3RequestStreamValidationHandler.newServerValidator;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertException;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertFrameEquals;
import static io.netty.incubator.codec.http3.Http3TestUtils.verifyClose;
import static io.netty.util.ReferenceCountUtil.release;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


public class Http3RequestStreamValidationHandlerTest extends Http3FrameTypeValidationHandlerTest {
    private final QpackDecoder decoder;

    public Http3RequestStreamValidationHandlerTest() {
        super(true, true);
        decoder = new QpackDecoder(new DefaultHttp3SettingsFrame());
    }

    @Override
    protected ChannelHandler newHandler(boolean server) {
        return new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(QuicStreamChannel ch) {
                Http3RequestStreamEncodeStateValidator encStateValidator = new Http3RequestStreamEncodeStateValidator();
                Http3RequestStreamDecodeStateValidator decStateValidator = new Http3RequestStreamDecodeStateValidator();
                ch.pipeline().addLast(encStateValidator);
                ch.pipeline().addLast(decStateValidator);
                ch.pipeline().addLast(newServerValidator(qpackAttributes, decoder, encStateValidator,
                        decStateValidator));
            }
        };
    }

    @Override
    protected List<Http3RequestStreamFrame> newValidFrames() {
        return Arrays.asList(new DefaultHttp3HeadersFrame(), new DefaultHttp3DataFrame(Unpooled.directBuffer()),
                new DefaultHttp3UnknownFrame(Http3CodecUtils.MAX_RESERVED_FRAME_TYPE, Unpooled.buffer()));
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInvalidFrameSequenceStartInbound(boolean server) throws Exception {
        setUp(server);
        final EmbeddedQuicStreamChannel channel = newStream(QuicStreamType.BIDIRECTIONAL, newHandler(server));
        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.buffer());

        Exception e = assertThrows(Exception.class, () -> channel.writeInbound(dataFrame));
        assertException(H3_FRAME_UNEXPECTED, e);

        verifyClose(H3_FRAME_UNEXPECTED, parent);
        assertEquals(0, dataFrame.refCnt());
        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInvalidFrameSequenceEndInbound(boolean server) throws Exception {
        setUp(server);
        final EmbeddedQuicStreamChannel channel = newStream(QuicStreamType.BIDIRECTIONAL, newHandler(server));

        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3DataFrame dataFrame2 = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3DataFrame dataFrame3 = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3HeadersFrame trailersFrame = new DefaultHttp3HeadersFrame();

        assertTrue(channel.writeInbound(headersFrame));
        assertTrue(channel.writeInbound(dataFrame.retainedDuplicate()));
        assertTrue(channel.writeInbound(dataFrame2.retainedDuplicate()));
        assertTrue(channel.writeInbound(trailersFrame));

        Exception e = assertThrows(Exception.class, () -> channel.writeInbound(dataFrame3));
        assertException(H3_FRAME_UNEXPECTED, e);

        verifyClose(H3_FRAME_UNEXPECTED, parent);
        assertTrue(channel.finish());
        assertEquals(0, dataFrame3.refCnt());

        assertFrameEquals(headersFrame, channel.readInbound());
        assertFrameEquals(dataFrame, channel.readInbound());
        assertFrameEquals(dataFrame2, channel.readInbound());
        assertFrameEquals(trailersFrame, channel.readInbound());
        assertNull(channel.readInbound());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInvalidFrameSequenceStartOutbound(boolean server) throws Exception {
        setUp(server);
        EmbeddedQuicStreamChannel channel = newStream(QuicStreamType.BIDIRECTIONAL, newHandler(server));

        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.buffer());

        Exception e = assertThrows(Exception.class, () -> channel.writeOutbound(dataFrame));
       assertException(H3_FRAME_UNEXPECTED, e);

        assertFalse(channel.finish());
        assertEquals(0, dataFrame.refCnt());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInvalidFrameSequenceEndOutbound(boolean server) throws Exception {
        setUp(server);
        EmbeddedQuicStreamChannel channel = newStream(QuicStreamType.BIDIRECTIONAL, newHandler(server));

        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3DataFrame dataFrame2 = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3DataFrame dat3Frame3 = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3HeadersFrame trailersFrame = new DefaultHttp3HeadersFrame();
        assertTrue(channel.writeOutbound(headersFrame));
        assertTrue(channel.writeOutbound(dataFrame.retainedDuplicate()));
        assertTrue(channel.writeOutbound(dataFrame2.retainedDuplicate()));
        assertTrue(channel.writeOutbound(trailersFrame));

        Exception e = assertThrows(Exception.class, () -> channel.writeOutbound(dat3Frame3));
        assertException(H3_FRAME_UNEXPECTED, e);

        assertTrue(channel.finish());
        assertEquals(0, dat3Frame3.refCnt());

        assertFrameEquals(headersFrame, channel.readOutbound());
        assertFrameEquals(dataFrame, channel.readOutbound());
        assertFrameEquals(dataFrame2, channel.readOutbound());
        assertFrameEquals(trailersFrame, channel.readOutbound());
        assertNull(channel.readOutbound());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testGoawayReceivedBeforeWritingHeaders(boolean server) throws Exception {
        setUp(server);
        EmbeddedQuicStreamChannel channel = newClientStream(() -> true);

        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        Exception e = assertThrows(Exception.class, () -> channel.writeOutbound(headersFrame));
        assertException(H3_FRAME_UNEXPECTED, e);

        // We should have closed the channel.
        assertFalse(channel.isActive());
        assertFalse(channel.finish());
        assertNull(channel.readOutbound());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testGoawayReceivedAfterWritingHeaders(boolean server) throws Exception {
        setUp(server);
        AtomicBoolean goAway = new AtomicBoolean();
        EmbeddedQuicStreamChannel channel = newClientStream(goAway::get);

        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.buffer());
        assertTrue(channel.writeOutbound(headersFrame));
        goAway.set(true);
        assertTrue(channel.writeOutbound(dataFrame.retainedDuplicate()));
        assertTrue(channel.finish());
        assertFrameEquals(headersFrame, channel.readOutbound());
        assertFrameEquals(dataFrame, channel.readOutbound());

        assertNull(channel.readOutbound());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testClientHeadRequestWithContentLength(boolean server) throws Exception {
        setUp(server);
        EmbeddedQuicStreamChannel channel = newClientStream(() -> false);

        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        headersFrame.headers().method(HttpMethod.HEAD.asciiName());
        assertTrue(channel.writeOutbound(headersFrame));

        Http3HeadersFrame responseHeadersFrame = new DefaultHttp3HeadersFrame();
        responseHeadersFrame.headers().setLong(HttpHeaderNames.CONTENT_LENGTH, 10);

        assertTrue(channel.writeInbound(responseHeadersFrame));
        channel.pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);

        assertTrue(channel.finishAndReleaseAll());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testClientNonHeadRequestWithContentLengthNoData(boolean server) throws Exception {
        setUp(server);
        testClientNonHeadRequestWithContentLength(true, false);
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testClientNonHeadRequestWithContentLengthNoDataAndTrailers(boolean server) throws Exception {
        setUp(server);
        testClientNonHeadRequestWithContentLength(true, true);
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testClientNonHeadRequestWithContentLengthNotEnoughData(boolean server) throws Exception {
        setUp(server);
        testClientNonHeadRequestWithContentLength(false, false);
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testClientNonHeadRequestWithContentLengthNotEnoughDataAndTrailer(boolean server) throws Exception {
        setUp(server);
        testClientNonHeadRequestWithContentLength(false, true);
    }

    private void testClientNonHeadRequestWithContentLength(boolean noData, boolean trailers) throws Exception {
        EmbeddedQuicStreamChannel channel = newClientStream(() -> false);

        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        headersFrame.headers().method(HttpMethod.GET.asciiName());
        assertTrue(channel.writeOutbound(headersFrame));

        Http3HeadersFrame responseHeadersFrame = new DefaultHttp3HeadersFrame();
        responseHeadersFrame.headers().setLong(HttpHeaderNames.CONTENT_LENGTH, 10);

        assertTrue(channel.writeInbound(responseHeadersFrame));
        if (!noData) {
            assertTrue(channel.writeInbound(new DefaultHttp3DataFrame(Unpooled.buffer().writeZero(9))));
        }
        try {
            if (trailers) {
                channel.writeInbound(new DefaultHttp3HeadersFrame());
            } else {
                channel.pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
                channel.checkException();
            }
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_MESSAGE_ERROR, e);
        }
        assertTrue(channel.finishAndReleaseAll());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testServerWithContentLengthNoData(boolean server) throws Exception {
        setUp(server);
        testServerWithContentLength(true, false);
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testServerWithContentLengthNoDataAndTrailers(boolean server) throws Exception {
        setUp(server);
        testServerWithContentLength(true, true);
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testServerWithContentLengthNotEnoughData(boolean server) throws Exception {
        setUp(server);
        testServerWithContentLength(false, false);
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testServerWithContentLengthNotEnoughDataAndTrailer(boolean server) throws Exception {
        setUp(server);
        testServerWithContentLength(false, true);
    }

    private void testServerWithContentLength(boolean noData, boolean trailers) throws Exception {
        EmbeddedQuicStreamChannel channel = newServerStream();

        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        headersFrame.headers().setLong(HttpHeaderNames.CONTENT_LENGTH, 10);
        headersFrame.headers().method(HttpMethod.POST.asciiName());
        assertTrue(channel.writeInbound(headersFrame));

        if (!noData) {
            assertTrue(channel.writeInbound(new DefaultHttp3DataFrame(Unpooled.buffer().writeZero(9))));
        }
        try {
            if (trailers) {
                channel.writeInbound(new DefaultHttp3HeadersFrame());
            } else {
                channel.pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
                channel.checkException();
            }
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_MESSAGE_ERROR, e);
        }
        assertTrue(channel.finishAndReleaseAll());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testHttp3HeadersFrameWithConnectionHeader(boolean server) throws Exception {
        setUp(server);
        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        headersFrame.headers().add(HttpHeaderNames.CONNECTION, "something");
        testHeadersFrame(headersFrame, Http3ErrorCode.H3_MESSAGE_ERROR);
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testHttp3HeadersFrameWithTeHeaderAndInvalidValue(boolean server) throws Exception {
        setUp(server);
        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        headersFrame.headers().add(HttpHeaderNames.TE, "something");
        testHeadersFrame(headersFrame, Http3ErrorCode.H3_MESSAGE_ERROR);
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testHttp3HeadersFrameWithTeHeaderAndValidValue(boolean server) throws Exception {
        setUp(server);
        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        headersFrame.headers().add(HttpHeaderNames.TE, HttpHeaderValues.TRAILERS);
        testHeadersFrame(headersFrame, null);
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInformationalResponseAfterActualResponseServer(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(true, true, newResponse(OK), newResponse(CONTINUE));
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInformationalResponseAfterActualResponseClient(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(false, true, newResponse(OK), newResponse(CONTINUE));
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testMultiInformationalResponseServer(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(true, false, newResponse(CONTINUE), newResponse(CONTINUE), newResponse(OK));
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testMultiInformationalResponseClient(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(false, false, newResponse(CONTINUE), newResponse(CONTINUE), newResponse(OK));
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testMultiInformationalResponseAfterActualResponseServer(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(true, false, newResponse(CONTINUE), newResponse(CONTINUE), newResponse(OK));
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testMultiInformationalResponseAfterActualResponseClient(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(false, false, newResponse(CONTINUE), newResponse(CONTINUE), newResponse(OK));
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInformationalResponseWithDataAndTrailersServer(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(true, false, newResponse(CONTINUE), newResponse(OK),
                new DefaultHttp3DataFrame(Unpooled.buffer()),
                new DefaultHttp3HeadersFrame());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInformationalResponseWithDataAndTrailersClient(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(false, false, newResponse(CONTINUE), newResponse(OK),
                new DefaultHttp3DataFrame(Unpooled.buffer()),
                new DefaultHttp3HeadersFrame());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInformationalResponseWithDataServer(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(true, false, newResponse(CONTINUE), newResponse(OK),
                new DefaultHttp3DataFrame(Unpooled.buffer()));
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInformationalResponseWithDataClient(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(false, false, newResponse(CONTINUE), newResponse(OK),
                new DefaultHttp3DataFrame(Unpooled.buffer()));
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInformationalResponsePostDataServer(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(true, true, newResponse(OK),
                new DefaultHttp3DataFrame(Unpooled.buffer()), newResponse(CONTINUE));
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInformationalResponsePostDataClient(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(false, true, newResponse(OK),
                new DefaultHttp3DataFrame(Unpooled.buffer()), newResponse(CONTINUE));
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInformationalResponsePostTrailersServer(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(true, true, newResponse(OK),
                new DefaultHttp3DataFrame(Unpooled.buffer()), new DefaultHttp3HeadersFrame(), newResponse(CONTINUE));
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInformationalResponsePostTrailersClient(boolean server) throws Exception {
        setUp(server);
        testInformationalResponse(false, true, newResponse(OK),
                new DefaultHttp3DataFrame(Unpooled.buffer()), new DefaultHttp3HeadersFrame(), newResponse(CONTINUE));
    }

    private void testInformationalResponse(boolean server, boolean expectFail, Http3Frame... frames) throws Exception {
        EmbeddedQuicStreamChannel channel = server ? newServerStream() :
                newClientStream(() -> false);

        for (int i = 0; i < frames.length; i++) {
            Http3Frame frame = frames[i];
            Http3Frame read = null;
            try {
                if (server) {
                    assertTrue(channel.writeOutbound(frame));
                    if (expectFail && i == frames.length - 1) {
                        fail();
                    } else {
                        read = channel.readOutbound();
                    }
                } else {
                    assertTrue(channel.writeInbound(frame));
                    if (expectFail && i == frames.length - 1) {
                        fail();
                    } else {
                        read = channel.readInbound();
                    }
                }
                assertEquals(frame, read);
            } catch (Exception e) {
                assertException(H3_FRAME_UNEXPECTED, e);
                if (!server) {
                    verifyClose(H3_FRAME_UNEXPECTED, parent);
                }
            } finally {
                release(read);
            }
        }
        assertFalse(parent.finish());
        assertFalse(channel.finish());
    }

    private void testHeadersFrame(Http3HeadersFrame headersFrame, Http3ErrorCode code) throws Exception {
        EmbeddedQuicStreamChannel channel = newServerStream();
        try {
            assertTrue(channel.writeInbound(headersFrame));
            if (code != null) {
                fail();
            }
        } catch (Throwable cause) {
            if (code == null) {
                throw cause;
            }
            assertException(code, cause);
            assertEquals((Integer) code.code, channel.outputShutdownError());
        }
        // Only expect produced messages when there was no error.
        assertEquals(code == null, channel.finishAndReleaseAll());
    }

    private EmbeddedQuicStreamChannel newClientStream(final BooleanSupplier goAwayReceivedSupplier) throws Exception {
        return newStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(QuicStreamChannel ch) {
                Http3RequestStreamEncodeStateValidator encStateValidator = new Http3RequestStreamEncodeStateValidator();
                Http3RequestStreamDecodeStateValidator decStateValidator = new Http3RequestStreamDecodeStateValidator();
                ch.pipeline().addLast(encStateValidator);
                ch.pipeline().addLast(decStateValidator);
                ch.pipeline().addLast(newClientValidator(goAwayReceivedSupplier, qpackAttributes, decoder,
                        encStateValidator, decStateValidator));
            }
        });
    }

    private EmbeddedQuicStreamChannel newServerStream() throws Exception {
        return newStream(QuicStreamType.BIDIRECTIONAL, newHandler(true));
    }

    private static Http3Frame newResponse(HttpResponseStatus status) {
        Http3HeadersFrame frame = new DefaultHttp3HeadersFrame();
        frame.headers().status(status.codeAsText());
        return frame;
    }
}
