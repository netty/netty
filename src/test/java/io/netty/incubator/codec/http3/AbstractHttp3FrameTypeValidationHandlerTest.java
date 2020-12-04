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

import io.netty.channel.Channel;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import org.junit.Test;

import java.util.List;

import static io.netty.incubator.codec.http3.Http3TestUtils.assertFrameReleased;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertFrameSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertException;
import static io.netty.incubator.codec.http3.Http3TestUtils.mockParent;
import static io.netty.incubator.codec.http3.Http3TestUtils.verifyClose;

public abstract class AbstractHttp3FrameTypeValidationHandlerTest<T extends Http3Frame> {

    protected abstract Http3FrameTypeValidationHandler<T> newHandler();

    protected abstract List<T> newValidFrames();

    protected abstract List<Http3Frame> newInvalidFrames();

    @Test
    public void testValidTypeInbound() {
        EmbeddedChannel channel = newChannel(newHandler());
        List<T> validFrames = newValidFrames();
        for (T valid : validFrames) {
            assertTrue(channel.writeInbound(valid));
            T read = channel.readInbound();
            assertFrameSame(valid, read);
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testValidTypeOutput() {
        EmbeddedChannel channel = newChannel(newHandler());
        List<T> validFrames = newValidFrames();
        for (T valid : validFrames) {
            assertTrue(channel.writeOutbound(valid));
            T read = channel.readOutbound();
            assertFrameSame(valid, read);
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testInvalidTypeInbound() {
        QuicChannel parent = mockParent();
        EmbeddedChannel channel = newChannel(parent, newHandler());

        List<Http3Frame> invalidFrames = newInvalidFrames();
        for (Http3Frame invalid : invalidFrames) {
            try {
                channel.writeInbound(invalid);
                fail();
            } catch (Exception e) {
                assertException(Http3ErrorCode.H3_FRAME_UNEXPECTED, e);
            }
            assertFrameReleased(invalid);
        }
        verifyClose(invalidFrames.size(), Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
        assertFalse(channel.finish());
    }

    @Test
    public void testInvalidTypeOutput() {
        EmbeddedChannel channel = newChannel(newHandler());

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

    private EmbeddedChannel newChannel(Http3FrameTypeValidationHandler<T> handler) {
        return newChannel(null, handler);
    }

    protected EmbeddedChannel newChannel(Channel parent, Http3FrameTypeValidationHandler<T> handler) {
        return new EmbeddedChannel(parent, DefaultChannelId.newInstance(), true, false, handler);
    }
}
