/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the WebSocket08FrameEncoder and Decoder implementation.<br>
 * Checks whether the combination of encoding and decoding yields the original data.<br>
 * Thereby also the masking behavior is checked.
 */
public class WebSocket08EncoderDecoderTest {

    private ByteBuf binTestData;
    private String strTestData;

    private static final int MAX_TESTDATA_LENGTH = 100 * 1024;

    private void initTestData() {
        binTestData = Unpooled.buffer(MAX_TESTDATA_LENGTH);
        byte j = 0;
        for (int i = 0; i < MAX_TESTDATA_LENGTH; i++) {
            binTestData.array()[i] = j;
            j++;
        }

        StringBuilder s = new StringBuilder();
        char c = 'A';
        for (int i = 0; i < MAX_TESTDATA_LENGTH; i++) {
            s.append(c);
            c++;
            if (c == 'Z') {
                c = 'A';
            }
        }
        strTestData = s.toString();
    }

    @Test
    public void testWebSocketProtocolViolation() {
        // Given
        initTestData();

        int maxPayloadLength = 255;
        String errorMessage = "Max frame length of " + maxPayloadLength + " has been exceeded.";
        WebSocketCloseStatus expectedStatus = WebSocketCloseStatus.MESSAGE_TOO_BIG;

        // With auto-close
        WebSocketDecoderConfig config = WebSocketDecoderConfig.newBuilder()
            .maxFramePayloadLength(maxPayloadLength)
            .closeOnProtocolViolation(true)
            .build();
        EmbeddedChannel inChannel = new EmbeddedChannel(new WebSocket08FrameDecoder(config));
        EmbeddedChannel outChannel = new EmbeddedChannel(new WebSocket08FrameEncoder(true));

        executeProtocolViolationTest(outChannel, inChannel, maxPayloadLength + 1, expectedStatus, errorMessage);

        CloseWebSocketFrame response = inChannel.readOutbound();
        assertNotNull(response);
        assertEquals(expectedStatus.code(), response.statusCode());
        assertEquals(errorMessage, response.reasonText());
        response.release();

        assertFalse(inChannel.finish());
        assertFalse(outChannel.finish());

        // Without auto-close
        config = WebSocketDecoderConfig.newBuilder()
            .maxFramePayloadLength(maxPayloadLength)
            .closeOnProtocolViolation(false)
            .build();
        inChannel = new EmbeddedChannel(new WebSocket08FrameDecoder(config));
        outChannel = new EmbeddedChannel(new WebSocket08FrameEncoder(true));

        executeProtocolViolationTest(outChannel, inChannel, maxPayloadLength + 1, expectedStatus, errorMessage);

        response = inChannel.readOutbound();
        assertNull(response);

        assertFalse(inChannel.finish());
        assertFalse(outChannel.finish());

        // Release test data
        binTestData.release();
    }

    private void executeProtocolViolationTest(EmbeddedChannel outChannel, EmbeddedChannel inChannel,
            int testDataLength, WebSocketCloseStatus expectedStatus, String errorMessage) {
        CorruptedWebSocketFrameException corrupted = null;

        try {
            testBinaryWithLen(outChannel, inChannel, testDataLength);
        } catch (CorruptedWebSocketFrameException e) {
            corrupted = e;
        }

        BinaryWebSocketFrame exceedingFrame = inChannel.readInbound();
        assertNull(exceedingFrame);

        assertNotNull(corrupted);
        assertEquals(expectedStatus, corrupted.closeStatus());
        assertEquals(errorMessage, corrupted.getMessage());
    }

    @Test
    public void testWebSocketEncodingAndDecoding() {
        initTestData();

        // Test without masking
        EmbeddedChannel outChannel = new EmbeddedChannel(new WebSocket08FrameEncoder(false));
        EmbeddedChannel inChannel = new EmbeddedChannel(new WebSocket08FrameDecoder(false, false, 1024 * 1024, false));
        executeTests(outChannel, inChannel);

        // Test with activated masking
        outChannel = new EmbeddedChannel(new WebSocket08FrameEncoder(true));
        inChannel = new EmbeddedChannel(new WebSocket08FrameDecoder(true, false, 1024 * 1024, false));
        executeTests(outChannel, inChannel);

        // Test with activated masking and an unmasked expecting but forgiving decoder
        outChannel = new EmbeddedChannel(new WebSocket08FrameEncoder(true));
        inChannel = new EmbeddedChannel(new WebSocket08FrameDecoder(false, false, 1024 * 1024, true));
        executeTests(outChannel, inChannel);

        // Release test data
        binTestData.release();
    }

    private void executeTests(EmbeddedChannel outChannel, EmbeddedChannel inChannel) {
        // Test at the boundaries of each message type, because this shifts the position of the mask field
        // Test min. 4 lengths to check for problems related to an uneven frame length
        executeTests(outChannel, inChannel, 0);
        executeTests(outChannel, inChannel, 1);
        executeTests(outChannel, inChannel, 2);
        executeTests(outChannel, inChannel, 3);
        executeTests(outChannel, inChannel, 4);
        executeTests(outChannel, inChannel, 5);

        executeTests(outChannel, inChannel, 125);
        executeTests(outChannel, inChannel, 126);
        executeTests(outChannel, inChannel, 127);
        executeTests(outChannel, inChannel, 128);
        executeTests(outChannel, inChannel, 129);

        executeTests(outChannel, inChannel, 65535);
        executeTests(outChannel, inChannel, 65536);
        executeTests(outChannel, inChannel, 65537);
        executeTests(outChannel, inChannel, 65538);
        executeTests(outChannel, inChannel, 65539);
    }

    private void executeTests(EmbeddedChannel outChannel, EmbeddedChannel inChannel, int testDataLength) {
        testTextWithLen(outChannel, inChannel, testDataLength);
        testBinaryWithLen(outChannel, inChannel, testDataLength);
    }

    private void testTextWithLen(EmbeddedChannel outChannel, EmbeddedChannel inChannel, int testDataLength) {
        String testStr = strTestData.substring(0, testDataLength);
        outChannel.writeOutbound(new TextWebSocketFrame(testStr));

        transfer(outChannel, inChannel);

        Object decoded = inChannel.readInbound();
        assertNotNull(decoded);
        assertTrue(decoded instanceof TextWebSocketFrame);
        TextWebSocketFrame txt = (TextWebSocketFrame) decoded;
        assertEquals(txt.text(), testStr);
        txt.release();
    }

    private void testBinaryWithLen(EmbeddedChannel outChannel, EmbeddedChannel inChannel, int testDataLength) {
        binTestData.retain(); // need to retain for sending and still keeping it
        binTestData.setIndex(0, testDataLength); // Send only len bytes
        outChannel.writeOutbound(new BinaryWebSocketFrame(binTestData));

        transfer(outChannel, inChannel);

        Object decoded = inChannel.readInbound();
        assertNotNull(decoded);
        assertTrue(decoded instanceof BinaryWebSocketFrame);
        BinaryWebSocketFrame binFrame = (BinaryWebSocketFrame) decoded;
        int readable = binFrame.content().readableBytes();
        assertEquals(readable, testDataLength);
        for (int i = 0; i < testDataLength; i++) {
            assertEquals(binTestData.getByte(i), binFrame.content().getByte(i));
        }
        binFrame.release();
    }

    private void transfer(EmbeddedChannel outChannel, EmbeddedChannel inChannel) {
        // Transfer encoded data into decoder
        // Loop because there might be multiple frames (gathering write)
        for (;;) {
            ByteBuf encoded = outChannel.readOutbound();
            if (encoded == null) {
                return;
            }
            inChannel.writeInbound(encoded);
        }
    }
}
