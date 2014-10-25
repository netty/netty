/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Assert;
import org.junit.Test;

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

        // Transfer encoded data into decoder
        // Loop because there might be multiple frames (gathering write)
        while (true) {
            ByteBuf encoded = outChannel.readOutbound();
            if (encoded != null) {
                inChannel.writeInbound(encoded);
            } else {
                break;
            }
        }

        Object decoded = inChannel.readInbound();
        Assert.assertNotNull(decoded);
        Assert.assertTrue(decoded instanceof TextWebSocketFrame);
        TextWebSocketFrame txt = (TextWebSocketFrame) decoded;
        Assert.assertEquals(txt.text(), testStr);
        txt.release();
    }

    private void testBinaryWithLen(EmbeddedChannel outChannel, EmbeddedChannel inChannel, int testDataLength) {
        binTestData.retain(); // need to retain for sending and still keeping it
        binTestData.setIndex(0, testDataLength); // Send only len bytes
        outChannel.writeOutbound(new BinaryWebSocketFrame(binTestData));

        // Transfer encoded data into decoder
        // Loop because there might be multiple frames (gathering write)
        while (true) {
            ByteBuf encoded = outChannel.readOutbound();
            if (encoded != null) {
                inChannel.writeInbound(encoded);
            } else {
                break;
            }
        }

        Object decoded = inChannel.readInbound();
        Assert.assertNotNull(decoded);
        Assert.assertTrue(decoded instanceof BinaryWebSocketFrame);
        BinaryWebSocketFrame binFrame = (BinaryWebSocketFrame) decoded;
        int readable = binFrame.content().readableBytes();
        Assert.assertEquals(readable, testDataLength);
        for (int i = 0; i < testDataLength; i++) {
            Assert.assertEquals(binTestData.getByte(i), binFrame.content().getByte(i));
        }
        binFrame.release();
    }
}
