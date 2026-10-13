/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.mqtt;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import static io.netty.handler.codec.mqtt.MqttReasonCodes.Disconnect.MALFORMED_PACKET;
import static io.netty.handler.codec.mqtt.MqttReasonCodes.Disconnect.PROTOCOL_ERROR;
import static io.netty.handler.codec.mqtt.MqttReasonCodes.Disconnect.TOPIC_NAME_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MqttDecoderExceptionTest {

    private static final byte[] CONNECT_MQTT_5 = {
            0x10, 15, 0, 4, 'M', 'Q', 'T', 'T', 5, 2, 0, 60, 0, 0, 2, 'k', 'a'
    };

    @Test
    void invalidPublishTopicHasTopicNameReasonCode() {
        MqttDecoderException error = decodeInvalid(new byte[] {
                0x30, 10, 0, 6, 't', 'e', 's', 't', '/', '+', 0, 'x'
        }, false);
        assertEquals(TOPIC_NAME_INVALID, error.reasonCode());
        assertEquals("invalid publish topic name: test/+ (contains wildcards)", error.getMessage());
    }

    @Test
    void invalidMessageIdHasProtocolErrorReasonCode() {
        MqttDecoderException error = decodeInvalid(new byte[] {
                0x32, 7, 0, 1, 'a', 0, 0, 0, 'x'
        }, false);
        assertEquals(PROTOCOL_ERROR, error.reasonCode());
        assertEquals("invalid messageId: 0", error.getMessage());
    }

    @Test
    void malformedUtf8HasMalformedPacketReasonCodeAndKeepsCause() {
        MqttDecoderException error = decodeInvalid(new byte[] {
                0x30, 5, 0, 1, (byte) 0xc3, 0, 'x'
        }, true);
        assertEquals(MALFORMED_PACKET, error.reasonCode());
        assertInstanceOf(java.nio.charset.CharacterCodingException.class, error.getCause());
    }

    @Test
    void illegalPublishQosHasMalformedPacketReasonCode() {
        MqttDecoderException error = decodeInvalid(new byte[] { 0x36, 0 }, false);
        assertEquals(MALFORMED_PACKET, error.reasonCode());
        assertEquals("Illegal QOS Level in fixed header of PUBLISH message (3)", error.getMessage());
    }

    @Test
    void reasonCodeExceptionRemainsADecoderException() {
        MqttDecoderException error = new MqttDecoderException(MALFORMED_PACKET, "bad message");
        assertInstanceOf(DecoderException.class, error);
        assertEquals("bad message", error.getMessage());
    }

    @Test
    void nullCharacterHasMalformedPacketReasonCode() {
        MqttDecoderException error = decodeInvalid(new byte[] {
                0x30, 5, 0, 1, 0, 0, 'x'
        }, true);

        assertEquals(MALFORMED_PACKET, error.reasonCode());
        assertEquals(
                "MQTT UTF-8 Encoded String must not contain U+0000",
                error.getMessage());
    }

    @Test
    void unknownPropertyHasMalformedPacketReasonCode() {
        MqttDecoderException error = decodeInvalid(new byte[] {
                0x30, 7, 0, 1, 'a', 2, 0x7f, 0, 'x'
        }, false);

        assertEquals(MALFORMED_PACKET, error.reasonCode());
        assertEquals("Unknown property type: 127", error.getMessage());
    }

    @Test
    void nullReasonCodeIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new MqttDecoderException(null, "bad message"));
    }
    private static MqttDecoderException decodeInvalid(byte[] invalidPacket, boolean strictUtf8) {
        EmbeddedChannel channel = new EmbeddedChannel(new MqttDecoder(8192, 23, strictUtf8));
        try {
            assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(CONNECT_MQTT_5)));
            MqttMessage connect = channel.readInbound();
            try {
                assertTrue(connect.decoderResult().isSuccess());
            } finally {
                ReferenceCountUtil.release(connect);
            }

            assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(invalidPacket)));
            MqttMessage invalid = channel.readInbound();
            try {
                assertTrue(invalid.decoderResult().isFailure());
                return assertInstanceOf(MqttDecoderException.class, invalid.decoderResult().cause());
            } finally {
                ReferenceCountUtil.release(invalid);
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
