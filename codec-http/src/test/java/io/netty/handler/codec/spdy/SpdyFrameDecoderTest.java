/*
* Copyright 2013 The Netty Project
*
* The Netty Project licenses this file to you under the Apache License,
* version 2.0 (the "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at:
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations
* under the License.
*/
package io.netty.handler.codec.spdy;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static io.netty.handler.codec.spdy.SpdyConstants.*;
import static org.junit.Assert.*;

public final class SpdyFrameDecoderTest {
    private InternalLogger logger = InternalLoggerFactory.getInstance(SpdyFrameDecoderTest.class);
    @Test
    public void testTooLargeHeaderNameOnSynStreamRequest() throws Exception {
        for (int version = SPDY_MIN_VERSION; version <= SPDY_MAX_VERSION; version++) {
            final int finalVersion = version;
            List<Integer> headerSizes = Arrays.asList(90, 900);
            for (final int maxHeaderSize : headerSizes) { // 90 catches the header name, 900 the value
                SpdyHeadersFrame frame = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);
                addHeader(frame, 100, 1000);
                EmbeddedChannel embedder = new EmbeddedChannel(
                        new LoggingHandler(),
                        new SpdyFrameDecoder(finalVersion, 10000, maxHeaderSize),
                        new SpdySessionHandler(finalVersion, true),
                        new SpdyFrameEncoder(finalVersion)
                        );
                // Pass frame through encoder
                embedder.writeOutbound(frame);
                // Read encoded data
                Object frameAfterEncoding = embedder.readOutbound();
                assertNull("Only one frame should be written", embedder.readOutbound());
                embedder.writeInbound(frameAfterEncoding);
                Object receivedObject = embedder.readInbound();
                assertNull("Only one frame should be received", embedder.readInbound());
                assertNotNull("version " + version + ", not null message",
                        receivedObject);
                String message = "version " + version + ", should be SpdyHeadersFrame, was " +
                        receivedObject.getClass();
                assertTrue(
                        message,
                        receivedObject instanceof SpdyHeadersFrame);
                SpdyHeadersFrame receivedFrame = (SpdyHeadersFrame) receivedObject;
                assertTrue("should be truncated", receivedFrame.isTruncated());
                assertFalse("should not be invalid", receivedFrame.isInvalid());
                embedder.finish();
            }
        }
    }

    private static void addHeader(SpdyHeadersFrame frame, int headerNameSize, int headerValueSize) {
        frame.headers().add("k", "v");
        StringBuilder headerName = new StringBuilder();
        for (int i = 0; i < headerNameSize; i++) {
            headerName.append('h');
        }
        StringBuilder headerValue = new StringBuilder();
        for (int i = 0; i < headerValueSize; i++) {
            headerValue.append('a');
        }
        frame.headers().add(headerName.toString(), headerValue.toString());
    }
}
