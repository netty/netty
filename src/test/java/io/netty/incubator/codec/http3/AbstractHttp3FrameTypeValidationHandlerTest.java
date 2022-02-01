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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.netty.incubator.codec.http3.Http3.getQpackAttributes;
import static io.netty.incubator.codec.http3.Http3.setQpackAttributes;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertException;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertFrameReleased;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertFrameSame;
import static io.netty.incubator.codec.http3.Http3TestUtils.verifyClose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class AbstractHttp3FrameTypeValidationHandlerTest<T extends Http3Frame> {

    private final QuicStreamType defaultStreamType;
    private final boolean isOutbound;
    private final boolean isInbound;
    protected EmbeddedQuicChannel parent;
    protected QpackAttributes qpackAttributes;

    protected abstract ChannelHandler newHandler(boolean server);

    protected abstract List<T> newValidFrames();

    protected abstract List<Http3Frame> newInvalidFrames();

    protected AbstractHttp3FrameTypeValidationHandlerTest(QuicStreamType defaultStreamType,
                                                          boolean isInbound, boolean isOutbound) {
        this.defaultStreamType = defaultStreamType;
        this.isInbound = isInbound;
        this.isOutbound = isOutbound;
    }

    static Collection<Boolean> data() {
        return Arrays.asList(true, false);
    }

    protected void setUp(boolean server) {
        parent = new EmbeddedQuicChannel(server);
        qpackAttributes = new QpackAttributes(parent, false);
        setQpackAttributes(parent, qpackAttributes);
    }

    @AfterEach
    public void tearDown() {
        if (parent != null) {
            final QpackAttributes qpackAttributes = getQpackAttributes(parent);
            if (qpackAttributes.decoderStreamAvailable()) {
                assertFalse(((EmbeddedQuicStreamChannel) qpackAttributes.decoderStream()).finish());
            }
            if (qpackAttributes.encoderStreamAvailable()) {
                assertFalse(((EmbeddedQuicStreamChannel) qpackAttributes.encoderStream()).finish());
            }
            assertFalse(parent.finish());
        }
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testValidTypeInbound(boolean server) throws Exception {
        assumeTrue(isInbound);
        setUp(server);
        final EmbeddedQuicStreamChannel channel = newStream(defaultStreamType, newHandler(server));
        List<T> validFrames = newValidFrames();
        for (T valid : validFrames) {
            assertTrue(channel.writeInbound(valid));
            T read = channel.readInbound();
            assertFrameSame(valid, read);
            if (valid instanceof Http3SettingsFrame) {
                afterSettingsFrameRead((Http3SettingsFrame) valid);
            }
        }
        assertFalse(channel.finish());
    }

    protected void afterSettingsFrameRead(Http3SettingsFrame settingsFrame) {
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testValidTypeOutbound(boolean server) throws Exception {
        assumeTrue(isOutbound);
        setUp(server);
        final EmbeddedQuicStreamChannel channel = newStream(defaultStreamType, newHandler(server));
        List<T> validFrames = newValidFrames();
        for (T valid : validFrames) {
            assertTrue(channel.writeOutbound(valid));
            T read = channel.readOutbound();
            assertFrameSame(valid, read);
        }
        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInvalidTypeInbound(boolean server) throws Exception {
        assumeTrue(isInbound);
        setUp(server);
        final EmbeddedQuicStreamChannel channel = newStream(defaultStreamType, newHandler(server));

        Http3ErrorCode errorCode = inboundErrorCodeInvalid();
        List<Http3Frame> invalidFrames = newInvalidFrames();
        for (Http3Frame invalid : invalidFrames) {
            Exception e = assertThrows(Exception.class, () -> channel.writeInbound(invalid));
            assertException(errorCode, e);

            assertFrameReleased(invalid);
        }
        verifyClose(invalidFrames.size(), errorCode, parent);
        assertFalse(channel.finish());
    }

    protected Http3ErrorCode inboundErrorCodeInvalid() {
        return Http3ErrorCode.H3_FRAME_UNEXPECTED;
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testInvalidTypeOutbound(boolean server) throws Exception {
        assumeTrue(isOutbound);
        setUp(server);
        final EmbeddedQuicStreamChannel channel = newStream(defaultStreamType, newHandler(server));

        List<Http3Frame> invalidFrames = newInvalidFrames();
        for (Http3Frame invalid : invalidFrames) {
            Exception e = assertThrows(Exception.class, () -> channel.writeOutbound(invalid));
            Http3TestUtils.assertException(Http3ErrorCode.H3_FRAME_UNEXPECTED, e);

            assertFrameReleased(invalid);
        }
        assertFalse(channel.finish());
    }

    protected EmbeddedQuicStreamChannel qPACKEncoderStream() {
        assertTrue(qpackAttributes.encoderStreamAvailable());
        return (EmbeddedQuicStreamChannel) qpackAttributes.encoderStream();
    }

    protected EmbeddedQuicStreamChannel qPACKDecoderStream() {
        assertTrue(qpackAttributes.decoderStreamAvailable());
        return (EmbeddedQuicStreamChannel) qpackAttributes.decoderStream();
    }

    protected void readAndReleaseStreamHeader(EmbeddedQuicStreamChannel stream) {
        ByteBuf streamType = stream.readOutbound();
        assertEquals(streamType.readableBytes(), 1);
        ReferenceCountUtil.release(streamType);
    }

    protected EmbeddedQuicStreamChannel newStream(QuicStreamType streamType, ChannelHandler handler)
            throws Exception {
        return (EmbeddedQuicStreamChannel) parent.createStream(streamType, handler).get();
    }
}
