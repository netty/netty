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

package io.netty.handler.codec.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.Attribute;
import io.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.w3c.dom.Attr;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.*;
import static io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE;
import static io.netty.handler.codec.mqtt.MqttSubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MqttEncoder and MqttDecoder.
 */
public class MqttCodecTest {

    private static final String CLIENT_ID = "RANDOM_TEST_CLIENT";
    private static final String WILL_TOPIC = "/my_will";
    private static final String WILL_MESSAGE = "gone";
    private static final String USER_NAME = "happy_user";
    private static final String PASSWORD = "123_or_no_pwd";

    private static final int KEEP_ALIVE_SECONDS = 600;

    private static final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

    private static final int DEFAULT_MAX_BYTES_IN_MESSAGE = 8092;

    @Mock
    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

    @Mock
    private final Channel channel = mock(Channel.class);

    @Mock
    private final Attribute<MqttVersion> versionAttrMock = mock(Attribute.class);

    private final MqttDecoder mqttDecoder = new MqttDecoder();

    /**
     * MqttDecoder with an unrealistic max payload size of 1 byte.
     */
    private final MqttDecoder mqttDecoderLimitedMessageSize = new MqttDecoder(1);

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.alloc()).thenReturn(ALLOCATOR);
        when(channel.attr(MqttCodecUtil.MQTT_VERSION_KEY)).thenReturn(versionAttrMock);
    }

    @Test
    public void testConnectMessageForMqtt31() throws Exception {
        final MqttConnectMessage message = createConnectMessage(MqttVersion.MQTT_3_1);
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnectMessage decodedMessage = (MqttConnectMessage) out.get(0);

        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnectVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateConnectPayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testConnectMessageForMqtt311() throws Exception {
        final MqttConnectMessage message = createConnectMessage(MqttVersion.MQTT_3_1_1);
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnectMessage decodedMessage = (MqttConnectMessage) out.get(0);

        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnectVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateConnectPayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testConnectMessageWithNonZeroReservedFlagForMqtt311() throws Exception {
        final MqttConnectMessage message = createConnectMessage(MqttVersion.MQTT_3_1_1);
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);
        try {
            // Set the reserved flag in the CONNECT Packet to 1
            byteBuf.setByte(9, byteBuf.getByte(9) | 0x1);
            final List<Object> out = new LinkedList<Object>();
            mqttDecoder.decode(ctx, byteBuf, out);

            assertEquals("Expected one object but got " + out.size(), 1, out.size());

            final MqttMessage decodedMessage = (MqttMessage) out.get(0);
            assertTrue(decodedMessage.decoderResult().isFailure());
            Throwable cause = decodedMessage.decoderResult().cause();
            assertTrue(cause instanceof DecoderException);
            assertEquals("non-zero reserved flag", cause.getMessage());
        } finally {
            byteBuf.release();
        }
    }

    @Test
    public void testConnectMessageNoPassword() throws Exception {
        final MqttConnectMessage message = createConnectMessage(
                MqttVersion.MQTT_3_1_1,
                null,
                PASSWORD,
                MqttProperties.NO_PROPERTIES,
                MqttProperties.NO_PROPERTIES);

        try {
            ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);
        } catch (Exception cause) {
            assertTrue(cause instanceof EncoderException);
        }
    }

    @Test
    public void testConnAckMessage() throws Exception {
        final MqttConnAckMessage message = createConnAckMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnAckMessage decodedMessage = (MqttConnAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnAckVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
    }

    @Test
    public void testPublishMessage() throws Exception {
        final MqttPublishMessage message = createPublishMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttPublishMessage decodedMessage = (MqttPublishMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePublishVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validatePublishPayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testPubAckMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MqttMessageType.PUBACK);
    }

    @Test
    public void testPubRecMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MqttMessageType.PUBREC);
    }

    @Test
    public void testPubRelMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MqttMessageType.PUBREL);
    }

    @Test
    public void testPubCompMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MqttMessageType.PUBCOMP);
    }

    @Test
    public void testSubscribeMessage() throws Exception {
        final MqttSubscribeMessage message = createSubscribeMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttSubscribeMessage decodedMessage = (MqttSubscribeMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateMessageIdVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateSubscribePayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testSubAckMessage() throws Exception {
        final MqttSubAckMessage message = createSubAckMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttSubAckMessage decodedMessage = (MqttSubAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateMessageIdVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateSubAckPayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testSubAckMessageWithFailureInPayload() throws Exception {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(12345);
        MqttSubAckPayload mqttSubAckPayload = new MqttSubAckPayload(MqttQoS.FAILURE.value());
        MqttSubAckMessage message =
                new MqttSubAckMessage(mqttFixedHeader, mqttMessageIdVariableHeader, mqttSubAckPayload);

        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        MqttSubAckMessage decodedMessage = (MqttSubAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateMessageIdVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateSubAckPayload(message.payload(), decodedMessage.payload());
        assertEquals(1, decodedMessage.payload().grantedQoSLevels().size());
        assertEquals(MqttQoS.FAILURE, MqttQoS.valueOf(decodedMessage.payload().grantedQoSLevels().get(0)));
    }

    @Test
    public void testUnSubscribeMessage() throws Exception {
        final MqttUnsubscribeMessage message = createUnsubscribeMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttUnsubscribeMessage decodedMessage = (MqttUnsubscribeMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateMessageIdVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateUnsubscribePayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testUnsubAckMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MqttMessageType.UNSUBACK);
    }

    @Test
    public void testPingReqMessage() throws Exception {
        testMessageWithOnlyFixedHeader(MqttMessage.PINGREQ);
    }

    @Test
    public void testPingRespMessage() throws Exception {
        testMessageWithOnlyFixedHeader(MqttMessage.PINGRESP);
    }

    @Test
    public void testDisconnectMessage() throws Exception {
        testMessageWithOnlyFixedHeader(MqttMessage.DISCONNECT);
    }

    //All 0..F message type codes are valid in MQTT 5
    @Test
    public void testUnknownMessageType() throws Exception {

        final MqttMessage message = createMessageWithFixedHeader(MqttMessageType.PINGREQ);
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);
        try {
            // setting an invalid message type (15, reserved and forbidden by MQTT 3.1.1 spec)
            byteBuf.setByte(0, 0xF0);
            final List<Object> out = new LinkedList<Object>();
            mqttDecoder.decode(ctx, byteBuf, out);

            assertEquals("Expected one object but got " + out.size(), 1, out.size());

            final MqttMessage decodedMessage = (MqttMessage) out.get(0);
            assertTrue(decodedMessage.decoderResult().isFailure());
            Throwable cause = decodedMessage.decoderResult().cause();
            assertTrue(cause instanceof DecoderException);
            assertEquals("AUTH message requires at least MQTT 5", cause.getMessage());
        } finally {
            byteBuf.release();
        }
    }

    @Test
    public void testConnectMessageForMqtt31TooLarge() throws Exception {
        final MqttConnectMessage message = createConnectMessage(MqttVersion.MQTT_3_1);
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        try {
            final List<Object> out = new LinkedList<Object>();
            mqttDecoderLimitedMessageSize.decode(ctx, byteBuf, out);

            assertEquals("Expected one object but got " + out.size(), 1, out.size());

            final MqttMessage decodedMessage = (MqttMessage) out.get(0);

            validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
            validateConnectVariableHeader(message.variableHeader(),
                    (MqttConnectVariableHeader) decodedMessage.variableHeader());
            validateDecoderExceptionTooLargeMessage(decodedMessage);
        } finally {
            byteBuf.release();
        }
    }

    @Test
    public void testConnectMessageForMqtt311TooLarge() throws Exception {
        final MqttConnectMessage message = createConnectMessage(MqttVersion.MQTT_3_1_1);
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        try {
            final List<Object> out = new LinkedList<Object>();
            mqttDecoderLimitedMessageSize.decode(ctx, byteBuf, out);

            assertEquals("Expected one object but got " + out.size(), 1, out.size());

            final MqttMessage decodedMessage = (MqttMessage) out.get(0);

            validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
            validateConnectVariableHeader(message.variableHeader(),
                    (MqttConnectVariableHeader) decodedMessage.variableHeader());
            validateDecoderExceptionTooLargeMessage(decodedMessage);
        } finally {
            byteBuf.release();
        }
    }

    @Test
    public void testConnAckMessageTooLarge() throws Exception {
        final MqttConnAckMessage message = createConnAckMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        try {
            final List<Object> out = new LinkedList<Object>();
            mqttDecoderLimitedMessageSize.decode(ctx, byteBuf, out);

            assertEquals("Expected one object but got " + out.size(), 1, out.size());

            final MqttMessage decodedMessage = (MqttMessage) out.get(0);
            validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
            validateDecoderExceptionTooLargeMessage(decodedMessage);
        } finally {
            byteBuf.release();
        }
    }

    @Test
    public void testPublishMessageTooLarge() throws Exception {
        final MqttPublishMessage message = createPublishMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        try {
            final List<Object> out = new LinkedList<Object>();
            mqttDecoderLimitedMessageSize.decode(ctx, byteBuf, out);

            assertEquals("Expected one object but got " + out.size(), 1, out.size());

            final MqttMessage decodedMessage = (MqttMessage) out.get(0);

            validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
            validatePublishVariableHeader(message.variableHeader(),
                    (MqttPublishVariableHeader) decodedMessage.variableHeader());
            validateDecoderExceptionTooLargeMessage(decodedMessage);
        } finally {
            byteBuf.release();
        }
    }

    @Test
    public void testSubscribeMessageTooLarge() throws Exception {
        final MqttSubscribeMessage message = createSubscribeMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        try {
            final List<Object> out = new LinkedList<Object>();
            mqttDecoderLimitedMessageSize.decode(ctx, byteBuf, out);

            assertEquals("Expected one object but got " + out.size(), 1, out.size());

            final MqttMessage decodedMessage = (MqttMessage) out.get(0);
            validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
            validateMessageIdVariableHeader(message.variableHeader(),
                    (MqttMessageIdVariableHeader) decodedMessage.variableHeader());
            validateDecoderExceptionTooLargeMessage(decodedMessage);
        } finally {
            byteBuf.release();
        }
    }

    @Test
    public void testSubAckMessageTooLarge() throws Exception {
        final MqttSubAckMessage message = createSubAckMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        try {
            final List<Object> out = new LinkedList<Object>();
            mqttDecoderLimitedMessageSize.decode(ctx, byteBuf, out);

            assertEquals("Expected one object but got " + out.size(), 1, out.size());

            final MqttMessage decodedMessage = (MqttMessage) out.get(0);
            validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
            validateMessageIdVariableHeader(message.variableHeader(),
                    (MqttMessageIdVariableHeader) decodedMessage.variableHeader());
            validateDecoderExceptionTooLargeMessage(decodedMessage);
        } finally {
            byteBuf.release();
        }
    }

    @Test
    public void testUnSubscribeMessageTooLarge() throws Exception {
        final MqttUnsubscribeMessage message = createUnsubscribeMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        try {
            final List<Object> out = new LinkedList<Object>();
            mqttDecoderLimitedMessageSize.decode(ctx, byteBuf, out);

            assertEquals("Expected one object but got " + out.size(), 1, out.size());

            final MqttMessage decodedMessage = (MqttMessage) out.get(0);
            validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
            validateMessageIdVariableHeader(message.variableHeader(),
                    (MqttMessageIdVariableHeader) decodedMessage.variableHeader());
            validateDecoderExceptionTooLargeMessage(decodedMessage);
        } finally {
            byteBuf.release();
        }
    }

    @Test
    public void testConnectMessageForMqtt5() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(SESSION_EXPIRY_INTERVAL.value(), 10));
        props.add(new MqttProperties.StringProperty(AUTHENTICATION_METHOD.value(), "Plain"));
        MqttProperties willProps = new MqttProperties();
        willProps.add(new MqttProperties.IntegerProperty(WILL_DELAY_INTERVAL.value(), 100));
        final MqttConnectMessage message =
                createConnectMessage(MqttVersion.MQTT_5, USER_NAME, PASSWORD, props, willProps);
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnectMessage decodedMessage = (MqttConnectMessage) out.get(0);

        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnectVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateConnectPayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testConnAckMessageForMqtt5() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(SESSION_EXPIRY_INTERVAL.value(), 10));
        props.add(new MqttProperties.IntegerProperty(MAXIMUM_QOS.value(), 1));
        props.add(new MqttProperties.IntegerProperty(MAXIMUM_PACKET_SIZE.value(), 1000));
        final MqttConnAckMessage message = createConnAckMessage(props);
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnAckMessage decodedMessage = (MqttConnAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnAckVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
    }

    @Test
    public void testPublishMessageForMqtt5() throws Exception {
        when(versionAttrMock.get()).thenReturn(MqttVersion.MQTT_5);
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(PAYLOAD_FORMAT_INDICATOR.value(), 6));
        props.add(new MqttProperties.UserProperty("isSecret", "true"));
        props.add(new MqttProperties.UserProperty("isUrgent", "false"));
        assertEquals("User properties count mismatch",
                ((MqttProperties.UserProperties) props.getProperty(USER_PROPERTY.value())).value.size(), 2);
        final MqttPublishMessage message = createPublishMessage(props);
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttPublishMessage decodedMessage = (MqttPublishMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePublishVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validatePublishPayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testPubAckMessageForMqtt5() throws Exception {
        when(versionAttrMock.get()).thenReturn(MqttVersion.MQTT_5);
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(PAYLOAD_FORMAT_INDICATOR.value(), 6));
        //0x87 - Not Authorized
        final MqttMessage message = createPubAckMessage((byte) 0x87, props);
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttMessage decodedMessage = (MqttMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePubReplyVariableHeader((MqttPubReplyMessageVariableHeader) message.variableHeader(),
                (MqttPubReplyMessageVariableHeader) decodedMessage.variableHeader());
    }

    @Test
    public void testPubAckMessageSkipCodeForMqtt5() throws Exception {
        //Code 0 (Success) and no properties - skip encoding code and properties
        final MqttMessage message = createPubAckMessage((byte) 0, MqttProperties.NO_PROPERTIES);
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttMessage decodedMessage = (MqttMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePubReplyVariableHeader((MqttPubReplyMessageVariableHeader) message.variableHeader(),
                (MqttPubReplyMessageVariableHeader) decodedMessage.variableHeader());
    }

    @Test
    public void testSubAckMessageForMqtt5() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(PAYLOAD_FORMAT_INDICATOR.value(), 6));
        final MqttSubAckMessage message = createSubAckMessage(props);
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttSubAckMessage decodedMessage = (MqttSubAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePacketIdAndPropertiesVariableHeader(
                (MqttMessageIdAndPropertiesVariableHeader) message.variableHeader(),
                (MqttMessageIdAndPropertiesVariableHeader) decodedMessage.variableHeader());
    }

    @Test
    public void testSubscribeMessageForMqtt5() throws Exception {
        when(versionAttrMock.get()).thenReturn(MqttVersion.MQTT_5);

        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(PAYLOAD_FORMAT_INDICATOR.value(), 6));
        final MqttSubscribeMessage message = MqttMessageBuilders.subscribe()
                .messageId((short) 1)
                .properties(props)
                .addSubscription("/topic", new MqttSubscriptionOption(AT_LEAST_ONCE,
                        true,
                        true,
                        SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS))
                .build();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());
        final MqttSubscribeMessage decodedMessage = (MqttSubscribeMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        final MqttMessageIdAndPropertiesVariableHeader expectedHeader =
                (MqttMessageIdAndPropertiesVariableHeader) message.variableHeader();
        final MqttMessageIdAndPropertiesVariableHeader actualHeader =
                (MqttMessageIdAndPropertiesVariableHeader) decodedMessage.variableHeader();
        validatePacketIdAndPropertiesVariableHeader(expectedHeader, actualHeader);
        validateSubscribePayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testUnsubAckMessageForMqtt5() throws Exception {
        when(versionAttrMock.get()).thenReturn(MqttVersion.MQTT_5);

        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(PAYLOAD_FORMAT_INDICATOR.value(), 6));
        final MqttUnsubAckMessage message = MqttMessageBuilders.unsubAck()
                .packetId((short) 1)
                .properties(props)
                .addReasonCode((short) 0x83)
                .build();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttUnsubAckMessage decodedMessage = (MqttUnsubAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePacketIdAndPropertiesVariableHeader(
                (MqttMessageIdAndPropertiesVariableHeader) message.variableHeader(),
                (MqttMessageIdAndPropertiesVariableHeader) decodedMessage.variableHeader());
        assertEquals("Reason code list doesn't match", message.payload().unsubscribeReasonCodes(),
                decodedMessage.payload().unsubscribeReasonCodes());
    }

    @Test
    public void testDisconnectMessageForMqtt5() throws Exception {
        when(versionAttrMock.get()).thenReturn(MqttVersion.MQTT_5);

        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(SESSION_EXPIRY_INTERVAL.value(), 6));
        final MqttMessage message = MqttMessageBuilders.disconnect()
                .reasonCode((byte) 0x96) // Message rate too high
                .properties(props)
                .build();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());
        final MqttMessage decodedMessage = (MqttMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateReasonCodeAndPropertiesVariableHeader(
                (MqttReasonCodeAndPropertiesVariableHeader) message.variableHeader(),
                (MqttReasonCodeAndPropertiesVariableHeader) decodedMessage.variableHeader());
    }

    @Test
    public void testDisconnectMessageSkipCodeForMqtt5() throws Exception {
        //code 0 and no properties - skip encoding code and properties
        final MqttMessage message = MqttMessageBuilders.disconnect()
                .reasonCode((byte) 0) // ok
                .properties(MqttProperties.NO_PROPERTIES)
                .build();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());
        final MqttMessage decodedMessage = (MqttMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateReasonCodeAndPropertiesVariableHeader(
                (MqttReasonCodeAndPropertiesVariableHeader) message.variableHeader(),
                (MqttReasonCodeAndPropertiesVariableHeader) decodedMessage.variableHeader());
    }

    @Test
    public void testAuthMessageForMqtt5() throws Exception {
        when(versionAttrMock.get()).thenReturn(MqttVersion.MQTT_5);

        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.BinaryProperty(AUTHENTICATION_DATA.value(), "secret".getBytes(CharsetUtil.UTF_8)));
        final MqttMessage message = MqttMessageBuilders.auth()
                .reasonCode((byte) 0x18) // Continue authentication
                .properties(props)
                .build();
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());
        final MqttMessage decodedMessage = (MqttMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateReasonCodeAndPropertiesVariableHeader(
                (MqttReasonCodeAndPropertiesVariableHeader) message.variableHeader(),
                (MqttReasonCodeAndPropertiesVariableHeader) decodedMessage.variableHeader());
    }

    @Test
    public void testMqttVersionDetection() throws Exception {
        clearInvocations(versionAttrMock);
        //Encode CONNECT message so that encoder would initialize its version
        final MqttConnectMessage connectMessage = createConnectMessage(MqttVersion.MQTT_5);
        ByteBuf connectByteBuf = MqttEncoder.doEncode(ctx, connectMessage);

        verify(versionAttrMock, times(1)).set(MqttVersion.MQTT_5);
        clearInvocations(versionAttrMock);

        final List<Object> connectOut = new LinkedList<Object>();
        mqttDecoder.decode(ctx, connectByteBuf, connectOut);

        verify(versionAttrMock, times(1)).set(MqttVersion.MQTT_5);

        assertEquals("Expected one CONNECT object but got " + connectOut.size(), 1, connectOut.size());

        final MqttConnectMessage decodedConnectMessage = (MqttConnectMessage) connectOut.get(0);

        validateFixedHeaders(connectMessage.fixedHeader(), decodedConnectMessage.fixedHeader());
        validateConnectVariableHeader(connectMessage.variableHeader(), decodedConnectMessage.variableHeader());
        validateConnectPayload(connectMessage.payload(), decodedConnectMessage.payload());

        verifyNoMoreInteractions(versionAttrMock);
    }

    private void testMessageWithOnlyFixedHeader(MqttMessage message) throws Exception {
        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttMessage decodedMessage = (MqttMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
    }

    private void testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MqttMessageType messageType)
            throws Exception {
        MqttMessage message = createMessageWithFixedHeaderAndMessageIdVariableHeader(messageType);

        ByteBuf byteBuf = MqttEncoder.doEncode(ctx, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttMessage decodedMessage = (MqttMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateMessageIdVariableHeader(
                (MqttMessageIdVariableHeader) message.variableHeader(),
                (MqttMessageIdVariableHeader) decodedMessage.variableHeader());
    }

    // Factory methods of different MQTT
    // Message types to help testing

    private static MqttMessage createMessageWithFixedHeader(MqttMessageType messageType) {
        return new MqttMessage(new MqttFixedHeader(messageType, false, MqttQoS.AT_MOST_ONCE, false, 0));
    }

    private static MqttMessage createMessageWithFixedHeaderAndMessageIdVariableHeader(MqttMessageType messageType) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(
                        messageType,
                        false,
                        messageType == MqttMessageType.PUBREL ? MqttQoS.AT_LEAST_ONCE : MqttQoS.AT_MOST_ONCE,
                        false,
                        0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(12345);
        return new MqttMessage(mqttFixedHeader, mqttMessageIdVariableHeader);
    }

    private static MqttConnectMessage createConnectMessage(MqttVersion mqttVersion) {
        return createConnectMessage(mqttVersion,
                USER_NAME,
                PASSWORD,
                MqttProperties.NO_PROPERTIES,
                MqttProperties.NO_PROPERTIES);
    }

    private static MqttConnectMessage createConnectMessage(MqttVersion mqttVersion,
                                                           String username,
                                                           String password,
                                                           MqttProperties properties,
                                                           MqttProperties willProperties) {
        return MqttMessageBuilders.connect()
                .clientId(CLIENT_ID)
                .protocolVersion(mqttVersion)
                .username(username)
                .password(password.getBytes(CharsetUtil.UTF_8))
                .properties(properties)
                .willRetain(true)
                .willQoS(MqttQoS.AT_LEAST_ONCE)
                .willFlag(true)
                .willTopic(WILL_TOPIC)
                .willMessage(WILL_MESSAGE.getBytes(CharsetUtil.UTF_8))
                .willProperties(willProperties)
                .cleanSession(true)
                .keepAlive(KEEP_ALIVE_SECONDS)
                .build();
    }

    private static MqttConnAckMessage createConnAckMessage() {
        return createConnAckMessage(MqttProperties.NO_PROPERTIES);
    }

    private static MqttConnAckMessage createConnAckMessage(MqttProperties properties) {
        return MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .properties(properties)
                .sessionPresent(true)
                .build();
    }

    private static MqttPublishMessage createPublishMessage() {
        return createPublishMessage(MqttProperties.NO_PROPERTIES);
    }

    private static MqttPublishMessage createPublishMessage(MqttProperties properties) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_LEAST_ONCE, true, 0);
        MqttPublishVariableHeader mqttPublishVariableHeader = new MqttPublishVariableHeader("/abc",
                1234,
                properties);
        ByteBuf payload = ALLOCATOR.buffer();
        payload.writeBytes("whatever".getBytes(CharsetUtil.UTF_8));
        return new MqttPublishMessage(mqttFixedHeader, mqttPublishVariableHeader, payload);
    }

    private static MqttSubscribeMessage createSubscribeMessage() {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, true, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(12345);

        List<MqttTopicSubscription> topicSubscriptions = new LinkedList<MqttTopicSubscription>();
        topicSubscriptions.add(new MqttTopicSubscription("/abc", MqttQoS.AT_LEAST_ONCE));
        topicSubscriptions.add(new MqttTopicSubscription("/def", MqttQoS.AT_LEAST_ONCE));
        topicSubscriptions.add(new MqttTopicSubscription("/xyz", MqttQoS.EXACTLY_ONCE));

        MqttSubscribePayload mqttSubscribePayload = new MqttSubscribePayload(topicSubscriptions);
        return new MqttSubscribeMessage(mqttFixedHeader, mqttMessageIdVariableHeader, mqttSubscribePayload);
    }

    private static MqttSubAckMessage createSubAckMessage() {
        return createSubAckMessage(MqttProperties.NO_PROPERTIES);
    }

    private static MqttSubAckMessage createSubAckMessage(MqttProperties properties) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(12345);
        MqttSubAckPayload mqttSubAckPayload = new MqttSubAckPayload(1, 2, 0);
        return new MqttSubAckMessage(mqttFixedHeader, mqttMessageIdVariableHeader, mqttSubAckPayload);
    }

    private static MqttUnsubscribeMessage createUnsubscribeMessage() {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, true, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(12345);

        List<String> topics = new LinkedList<String>();
        topics.add("/abc");
        topics.add("/def");
        topics.add("/xyz");

        MqttUnsubscribePayload mqttUnsubscribePayload = new MqttUnsubscribePayload(topics);
        return new MqttUnsubscribeMessage(mqttFixedHeader, mqttMessageIdVariableHeader, mqttUnsubscribePayload);
    }

    private MqttMessage createPubAckMessage(byte reasonCode, MqttProperties properties) {
        return MqttMessageBuilders.pubAck()
                .packetId((short) 1)
                .reasonCode(reasonCode)
                .properties(properties)
                .build();
    }

    // Helper methods to compare expected and actual
    // MQTT messages

    private static void validateFixedHeaders(MqttFixedHeader expected, MqttFixedHeader actual) {
        assertEquals("MqttFixedHeader MqttMessageType mismatch ", expected.messageType(), actual.messageType());
        assertEquals("MqttFixedHeader Qos mismatch ", expected.qosLevel(), actual.qosLevel());
    }

    private static void validateConnectVariableHeader(
            MqttConnectVariableHeader expected,
            MqttConnectVariableHeader actual) {
        assertEquals("MqttConnectVariableHeader Name mismatch ", expected.name(), actual.name());
        assertEquals(
                "MqttConnectVariableHeader KeepAliveTimeSeconds mismatch ",
                expected.keepAliveTimeSeconds(),
                actual.keepAliveTimeSeconds());
        assertEquals("MqttConnectVariableHeader Version mismatch ", expected.version(), actual.version());
        assertEquals("MqttConnectVariableHeader Version mismatch ", expected.version(), actual.version());
        validateProperties(expected.properties(), actual.properties());
        assertEquals("MqttConnectVariableHeader WillQos mismatch ", expected.willQos(), actual.willQos());

        assertEquals("MqttConnectVariableHeader HasUserName mismatch ", expected.hasUserName(), actual.hasUserName());
        assertEquals("MqttConnectVariableHeader HasPassword mismatch ", expected.hasPassword(), actual.hasPassword());
        assertEquals(
                "MqttConnectVariableHeader IsCleanSession mismatch ",
                expected.isCleanSession(),
                actual.isCleanSession());
        assertEquals("MqttConnectVariableHeader IsWillFlag mismatch ", expected.isWillFlag(), actual.isWillFlag());
        assertEquals(
                "MqttConnectVariableHeader IsWillRetain mismatch ",
                expected.isWillRetain(),
                actual.isWillRetain());
    }

    private static void validateConnectPayload(MqttConnectPayload expected, MqttConnectPayload actual) {
        assertEquals(
                "MqttConnectPayload ClientIdentifier mismatch ",
                expected.clientIdentifier(),
                actual.clientIdentifier());
        assertEquals("MqttConnectPayload UserName mismatch ", expected.userName(), actual.userName());
        assertEquals("MqttConnectPayload Password mismatch ", expected.password(), actual.password());
        assertTrue(
                "MqttConnectPayload Password bytes mismatch ",
                Arrays.equals(expected.passwordInBytes(), actual.passwordInBytes()));
        assertEquals("MqttConnectPayload WillMessage mismatch ", expected.willMessage(), actual.willMessage());
        assertTrue(
                "MqttConnectPayload WillMessage bytes mismatch ",
                Arrays.equals(expected.willMessageInBytes(), actual.willMessageInBytes()));
        assertEquals("MqttConnectPayload WillTopic mismatch ", expected.willTopic(), actual.willTopic());
        validateProperties(expected.willProperties(), actual.willProperties());
    }

    private static void validateConnAckVariableHeader(
            MqttConnAckVariableHeader expected,
            MqttConnAckVariableHeader actual) {
        assertEquals(
                "MqttConnAckVariableHeader MqttConnectReturnCode mismatch",
                expected.connectReturnCode(),
                actual.connectReturnCode());
    }

    private static void validatePublishVariableHeader(
            MqttPublishVariableHeader expected,
            MqttPublishVariableHeader actual) {
        assertEquals("MqttPublishVariableHeader TopicName mismatch ", expected.topicName(), actual.topicName());
        assertEquals("MqttPublishVariableHeader MessageId mismatch ", expected.packetId(), actual.packetId());
        validateProperties(expected.properties(), actual.properties());
    }

    private static void validatePublishPayload(ByteBuf expected, ByteBuf actual) {
        assertEquals("PublishPayload mismatch ", 0, expected.compareTo(actual));
    }

    private static void validateMessageIdVariableHeader(
            MqttMessageIdVariableHeader expected,
            MqttMessageIdVariableHeader actual) {
        assertEquals("MqttMessageIdVariableHeader MessageId mismatch ", expected.messageId(), actual.messageId());
    }

    private static void validateSubscribePayload(MqttSubscribePayload expected, MqttSubscribePayload actual) {
        List<MqttTopicSubscription> expectedTopicSubscriptions = expected.topicSubscriptions();
        List<MqttTopicSubscription> actualTopicSubscriptions = actual.topicSubscriptions();

        assertEquals(
                "MqttSubscribePayload TopicSubscriptionList size mismatch ",
                expectedTopicSubscriptions.size(),
                actualTopicSubscriptions.size());
        for (int i = 0; i < expectedTopicSubscriptions.size(); i++) {
            validateTopicSubscription(expectedTopicSubscriptions.get(i), actualTopicSubscriptions.get(i));
        }
    }

    private static void validateTopicSubscription(
            MqttTopicSubscription expected,
            MqttTopicSubscription actual) {
        assertEquals("MqttTopicSubscription TopicName mismatch ", expected.topicName(), actual.topicName());
        assertEquals(
                "MqttTopicSubscription Qos mismatch ",
                expected.qualityOfService(),
                actual.qualityOfService());
        assertEquals(
                "MqttTopicSubscription options mismatch ",
                expected.option(),
                actual.option());
    }

    private static void validateSubAckPayload(MqttSubAckPayload expected, MqttSubAckPayload actual) {
        assertArrayEquals(
                "MqttSubAckPayload GrantedQosLevels mismatch ",
                expected.grantedQoSLevels().toArray(),
                actual.grantedQoSLevels().toArray());
    }

    private static void validateUnsubscribePayload(MqttUnsubscribePayload expected, MqttUnsubscribePayload actual) {
        assertArrayEquals(
                "MqttUnsubscribePayload TopicList mismatch ",
                expected.topics().toArray(),
                actual.topics().toArray());
    }

    private static void validateDecoderExceptionTooLargeMessage(MqttMessage message) {
        assertNull("MqttMessage payload expected null ", message.payload());
        assertTrue(message.decoderResult().isFailure());
        Throwable cause = message.decoderResult().cause();
        assertTrue("MqttMessage DecoderResult cause expected instance of DecoderException ",
                cause instanceof DecoderException);
        assertTrue("MqttMessage DecoderResult cause reason expect to contain 'too large message' ",
                cause.getMessage().contains("too large message:"));
    }

    private static void validatePubReplyVariableHeader(
            MqttPubReplyMessageVariableHeader expected,
            MqttPubReplyMessageVariableHeader actual) {
        assertEquals("MqttPubReplyMessageVariableHeader MessageId mismatch ",
                expected.messageId(), actual.messageId());
        assertEquals("MqttPubReplyMessageVariableHeader reasonCode mismatch ",
                expected.reasonCode(), actual.reasonCode());

        final MqttProperties expectedProps = expected.properties();
        final MqttProperties actualProps = actual.properties();
        validateProperties(expectedProps, actualProps);
    }

    private void validatePacketIdAndPropertiesVariableHeader(MqttMessageIdAndPropertiesVariableHeader expected,
                                                              MqttMessageIdAndPropertiesVariableHeader actual) {
        assertEquals("MqttMessageIdAndPropertiesVariableHeader MessageId mismatch ",
                expected.messageId(), actual.messageId());
        final MqttProperties expectedProps = expected.properties();
        final MqttProperties actualProps = actual.properties();
        validateProperties(expectedProps, actualProps);
    }

    private void validateReasonCodeAndPropertiesVariableHeader(MqttReasonCodeAndPropertiesVariableHeader expected,
                                                             MqttReasonCodeAndPropertiesVariableHeader actual) {
        assertEquals("MqttReasonCodeAndPropertiesVariableHeader reason mismatch ",
                expected.reasonCode(), actual.reasonCode());
        final MqttProperties expectedProps = expected.properties();
        final MqttProperties actualProps = actual.properties();
        validateProperties(expectedProps, actualProps);
    }

    private static void validateProperties(MqttProperties expected, MqttProperties actual) {
        for (MqttProperties.MqttProperty expectedProperty : expected.listAll()) {
            MqttProperties.MqttProperty actualProperty = actual.getProperty(expectedProperty.propertyId);
            switch (MqttProperties.MqttPropertyType.valueOf(expectedProperty.propertyId)) {
                // one byte value integer property
                case PAYLOAD_FORMAT_INDICATOR:
                case REQUEST_PROBLEM_INFORMATION:
                case REQUEST_RESPONSE_INFORMATION:
                case MAXIMUM_QOS:
                case RETAIN_AVAILABLE:
                case WILDCARD_SUBSCRIPTION_AVAILABLE:
                case SUBSCRIPTION_IDENTIFIER_AVAILABLE:
                case SHARED_SUBSCRIPTION_AVAILABLE: {
                    final Integer expectedValue = ((MqttProperties.IntegerProperty) expectedProperty).value;
                    final Integer actualValue = ((MqttProperties.IntegerProperty) actualProperty).value;
                    assertEquals("one byte property doesn't match", expectedValue, actualValue);
                    break;
                }
                // two byte value integer property
                case SERVER_KEEP_ALIVE:
                case RECEIVE_MAXIMUM:
                case TOPIC_ALIAS_MAXIMUM:
                case TOPIC_ALIAS: {
                    final Integer expectedValue = ((MqttProperties.IntegerProperty) expectedProperty).value;
                    final Integer actualValue = ((MqttProperties.IntegerProperty) actualProperty).value;
                    assertEquals("two byte property doesn't match", expectedValue, actualValue);
                    break;
                }
                // four byte value integer property
                case PUBLICATION_EXPIRY_INTERVAL:
                case SESSION_EXPIRY_INTERVAL:
                case WILL_DELAY_INTERVAL:
                case MAXIMUM_PACKET_SIZE: {
                    final Integer expectedValue = ((MqttProperties.IntegerProperty) expectedProperty).value;
                    final Integer actualValue = ((MqttProperties.IntegerProperty) actualProperty).value;
                    assertEquals("four byte property doesn't match", expectedValue, actualValue);
                    break;
                }
                // four byte value integer property
                case SUBSCRIPTION_IDENTIFIER: {
                    final Integer expectedValue = ((MqttProperties.IntegerProperty) expectedProperty).value;
                    final Integer actualValue = ((MqttProperties.IntegerProperty) actualProperty).value;
                    assertEquals("variable byte integer property doesn't match", expectedValue, actualValue);
                    break;
                }
                // UTF-8 string value integer property
                case CONTENT_TYPE:
                case RESPONSE_TOPIC:
                case ASSIGNED_CLIENT_IDENTIFIER:
                case AUTHENTICATION_METHOD:
                case RESPONSE_INFORMATION:
                case SERVER_REFERENCE:
                case REASON_STRING: {
                    final String expectedValue = ((MqttProperties.StringProperty) expectedProperty).value;
                    final String actualValue = ((MqttProperties.StringProperty) actualProperty).value;
                    assertEquals("String property doesn't match", expectedValue, actualValue);
                    break;
                }
                // User property
                case USER_PROPERTY: {
                    final List<MqttProperties.StringPair> expectedPairs =
                            ((MqttProperties.UserProperties) expectedProperty).value;
                    final List<MqttProperties.StringPair> actualPairs =
                            ((MqttProperties.UserProperties) actualProperty).value;
                    assertEquals("User properties count doesn't match", expectedPairs, actualPairs);
                    for (int i = 0; i < expectedPairs.size(); i++) {
                        assertEquals("User property mismatch", expectedPairs.get(i), actualPairs.get(i));
                    }
                    break;
                }
                // byte[] property
                case CORRELATION_DATA:
                case AUTHENTICATION_DATA: {
                    final byte[] expectedValue = ((MqttProperties.BinaryProperty) expectedProperty).value;
                    final byte[] actualValue = ((MqttProperties.BinaryProperty) actualProperty).value;
                    final String expectedHexDump = ByteBufUtil.hexDump(expectedValue);
                    final String actualHexDump = ByteBufUtil.hexDump(actualValue);
                    assertEquals("byte[] property doesn't match", expectedHexDump, actualHexDump);
                    break;
                }
                default:
                    fail("Property Id not recognized " + Integer.toHexString(expectedProperty.propertyId));
            }
        }
    }
}
