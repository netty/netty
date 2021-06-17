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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static io.netty.incubator.codec.http3.Http3.getQpackAttributes;
import static io.netty.incubator.codec.http3.Http3.setQpackAttributes;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertException;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertFrameReleased;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertFrameSame;
import static io.netty.incubator.codec.http3.Http3TestUtils.verifyClose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public abstract class AbstractHttp3FrameTypeValidationHandlerTest<T extends Http3Frame> {

    private final boolean server;
    private final QuicStreamType defaultStreamType;
    private final boolean isOutbound;
    private final boolean isInbound;
    protected EmbeddedQuicChannel parent;
    protected QpackAttributes qpackAttributes;

    protected abstract ChannelHandler newHandler();

    protected abstract List<T> newValidFrames();

    protected abstract List<Http3Frame> newInvalidFrames();

    protected AbstractHttp3FrameTypeValidationHandlerTest(boolean server, QuicStreamType defaultStreamType) {
        this.server = server;
        this.defaultStreamType = defaultStreamType;
        this.isOutbound = this instanceof ChannelOutboundHandler;
        this.isInbound = this instanceof ChannelInboundHandler;
    }

    @Before
    public void setUp() {
        parent = new EmbeddedQuicChannel(server);
        qpackAttributes = new QpackAttributes(parent, false);
        setQpackAttributes(parent, qpackAttributes);
    }

    @After
    public void tearDown() {
        final QpackAttributes qpackAttributes = getQpackAttributes(parent);
        if (qpackAttributes.decoderStreamAvailable()) {
            assertFalse(((EmbeddedQuicStreamChannel) qpackAttributes.decoderStream()).finish());
        }
        if (qpackAttributes.encoderStreamAvailable()) {
            assertFalse(((EmbeddedQuicStreamChannel) qpackAttributes.encoderStream()).finish());
        }
        assertFalse(parent.finish());
    }

    @Test
    public void testValidTypeInbound() throws Exception {
        assumeTrue(isInbound);
        final EmbeddedQuicStreamChannel channel = newStream(defaultStreamType, newHandler());
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

    @Test
    public void testValidTypeOutbound() throws Exception {
        assumeTrue(isOutbound);
        final EmbeddedQuicStreamChannel channel = newStream(defaultStreamType, newHandler());
        List<T> validFrames = newValidFrames();
        for (T valid : validFrames) {
            assertTrue(channel.writeOutbound(valid));
            T read = channel.readOutbound();
            assertFrameSame(valid, read);
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testInvalidTypeInbound() throws Exception {
        assumeTrue(isInbound);
        final EmbeddedQuicStreamChannel channel = newStream(defaultStreamType, newHandler());

        Http3ErrorCode errorCode = inboundErrorCodeInvalid();
        List<Http3Frame> invalidFrames = newInvalidFrames();
        for (Http3Frame invalid : invalidFrames) {
            try {
                channel.writeInbound(invalid);
                fail();
            } catch (Exception e) {
                assertException(errorCode, e);
            }
            assertFrameReleased(invalid);
        }
        verifyClose(invalidFrames.size(), errorCode, parent);
        assertFalse(channel.finish());
    }

    protected Http3ErrorCode inboundErrorCodeInvalid() {
        return Http3ErrorCode.H3_FRAME_UNEXPECTED;
    }

    @Test
    public void testInvalidTypeOutbound() throws Exception {
        assumeTrue(isOutbound);
        final EmbeddedQuicStreamChannel channel = newStream(defaultStreamType, newHandler());

        List<Http3Frame> invalidFrames = newInvalidFrames();
        for (Http3Frame invalid : invalidFrames) {
            try {
                channel.writeOutbound(invalid);
                fail();
            } catch (Exception e) {
                Http3TestUtils.assertException(Http3ErrorCode.H3_FRAME_UNEXPECTED, e);
            }
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
