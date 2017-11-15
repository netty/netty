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
import io.netty.handler.codec.mqtt.MqttProperties.MqttProperty;
import io.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedList;
import java.util.List;

import static io.netty.handler.codec.mqtt.MqttCodecTest.*;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.AUTHENTICATION_DATA;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL;
import static io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE;
import static io.netty.handler.codec.mqtt.SubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MqttEncoder and MqttDecoder for MQTT v5.
 */
public class Mqtt5CodecTest {

    private static final String CLIENT_ID = "RANDOM_TEST_CLIENT";
    private static final String WILL_TOPIC = "/my_will";
    private static final byte[] WILL_MESSAGE = "gone".getBytes(CharsetUtil.UTF_8);
    private static final String USER_NAME = "happy_user";
    private static final String PASSWORD = "123_or_no_pwd";

    private static final int KEEP_ALIVE_SECONDS = 600;

    private static final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

    @Mock
    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

    @Mock
    private final Channel channel = mock(Channel.class);

    private final MqttDecoder mqttDecoder = new MqttDecoderV5(new VariableHeaderDecoderV5());

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(ctx.channel()).thenReturn(channel);
    }

    @Test
    public void testConnectMessageForMqtt5() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(SESSION_EXPIRY_INTERVAL.value(), 10));
        final MqttConnectMessage message = createConnectV5Message(props);
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnectMessage decodedMessage = (MqttConnectMessage) out.get(0);

        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnectVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateConnectPayload(message.payload(), decodedMessage.payload());
    }

    private static MqttConnectMessage createConnectV5Message(MqttProperties properties) {
        return createConnectV5Message(USER_NAME, PASSWORD, properties);
    }

    private static MqttConnectMessage createConnectV5Message(
            String username,
            String password,
            MqttProperties properties) {
        return MqttMessageBuilders.connect()
                .clientId(CLIENT_ID)
                .protocolVersion(MqttVersion.MQTT_5)
                .username(username)
                .password(password.getBytes(CharsetUtil.UTF_8))
                .willRetain(true)
                .willQoS(AT_LEAST_ONCE)
                .willFlag(true)
                .willTopic(WILL_TOPIC)
                .willMessage(WILL_MESSAGE)
                .cleanSession(true)
                .keepAlive(KEEP_ALIVE_SECONDS)
                .properties(properties)
                .build();
    }

    @Test
    public void testConnAckMessage() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(SESSION_EXPIRY_INTERVAL.value(), 10));
        final MqttConnAckMessage message = createConnAckMessage(props);
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnAckMessage decodedMessage = (MqttConnAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnAckVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
    }

    private static void validateConnAckVariableHeader(
            MqttConnAckVariableHeader expected,
            MqttConnAckVariableHeader actual) {
        MqttCodecTest.validateConnAckVariableHeader(expected, actual);
        final MqttProperties expectedProps = expected.properties();
        final MqttProperties actualProps = actual.properties();
        assertEquals(expectedProps.listAll().iterator().next().value, actualProps.listAll().iterator().next().value);
    }

    private static MqttConnAckMessage createConnAckMessage(MqttProperties properties) {
        return MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(true)
                .properties(properties)
                .build();
    }

    @Test
    public void testPublish() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(PAYLOAD_FORMAT_INDICATOR.value(), 6));
        final MqttPublishMessage message = createPublishMessage(props);
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttPublishMessage decodedMessage = (MqttPublishMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePublishVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
    }

    private static void validatePublishVariableHeader(
            MqttPublishVariableHeader expected,
            MqttPublishVariableHeader actual) {
        MqttCodecTest.validatePublishVariableHeader(expected, actual);

        final MqttProperties expectedProps = expected.properties();
        final MqttProperties actualProps = actual.properties();
        assertEquals(expectedProps.listAll().iterator().next().value, actualProps.listAll().iterator().next().value);
    }

    @Test
    public void testPubAck() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(PAYLOAD_FORMAT_INDICATOR.value(), 6));
        final MqttMessage message = createPubAckMessage((byte) 0x87, props);
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttMessage decodedMessage = (MqttMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePubAckVariableHeader((MqttPubReplyMessageVariableHeader) message.variableHeader(),
                (MqttPubReplyMessageVariableHeader) decodedMessage.variableHeader());
    }

    private MqttMessage createPubAckMessage(byte reasonCode, MqttProperties properties) {
        return MqttMessageBuilders.pubAck()
                .packetId((short) 1)
                .reasonCode(reasonCode)
                .properties(properties)
                .build();
    }

    private static void validatePubAckVariableHeader(
            MqttPubReplyMessageVariableHeader expected,
            MqttPubReplyMessageVariableHeader actual) {
        assertEquals("MqttPubReplyMessageVariableHeader MessageId mismatch ",
                expected.messageId(), actual.messageId());
        assertEquals("MqttPubReplyMessageVariableHeader reasonCode mismatch ",
                expected.reasonCode(), actual.reasonCode());

        final MqttProperties expectedProps = expected.properties();
        final MqttProperties actualProps = actual.properties();
        assertEquals(expectedProps.listAll().iterator().next().value, actualProps.listAll().iterator().next().value);
    }

    @Test
    public void testSubAck() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(PAYLOAD_FORMAT_INDICATOR.value(), 6));
        final MqttSubAckMessage message = createSubAckMessage(props);
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttSubAckMessage decodedMessage = (MqttSubAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePacketIdPlusPropertiesVariableHeader(
                (MqttMessageIdPlusPropertiesVariableHeader) message.variableHeader(),
                (MqttMessageIdPlusPropertiesVariableHeader) decodedMessage.variableHeader());
    }

    private MqttSubAckMessage createSubAckMessage(MqttProperties properties) {
        return MqttMessageBuilders.subAck()
                .packetId((short) 1)
                .addGrantedQos(AT_LEAST_ONCE)
                .properties(properties)
                .build();
    }

    private void validatePacketIdPlusPropertiesVariableHeader(MqttMessageIdPlusPropertiesVariableHeader expected,
                                                              MqttMessageIdPlusPropertiesVariableHeader actual) {
        assertEquals("MqttMessageIdVariableHeader MessageId mismatch ",
                expected.messageId(), actual.messageId());
        final MqttProperties expectedProps = expected.properties();
        final MqttProperties actualProps = actual.properties();
        validateProperties(expectedProps, actualProps);
    }

    private void validateProperties(MqttProperties expected, MqttProperties actual) {
        for (MqttProperty expectedProperty : expected.listAll()) {
            MqttProperty actualProperty = actual.getProperty(expectedProperty.propertyId);
            switch (MqttProperties.MqttPropertyType.valueOf(expectedProperty.propertyId)) {
                // one byte value integer property
                case PAYLOAD_FORMAT_INDICATOR:
                case REQUEST_PROBLEM_INFORMATION:
                case REQUEST_RESPONSE_INFORMATION:
                case MAXIMUM_QOS:
                case RETAIN_AVAILABLE:
                case WILDCARD_SUBSCRIPTION_AVAILABLE:
                case SUBSCRIPTION_IDENTIFIER_AVAILABLE:
                case SHARED_SUBSCRIPTION_AVAILABLE:
                {
                    final Integer expectedValue = ((MqttProperties.IntegerProperty) expectedProperty).value;
                    final Integer actualValue = ((MqttProperties.IntegerProperty) actualProperty).value;
                    assertEquals("one byte property doesn't match", expectedValue, actualValue);
                    break;
                }
                // two byte value integer property
                case SERVER_KEEP_ALIVE:
                case RECEIVE_MAXIMUM:
                case TOPIC_ALIAS_MAXIMUM:
                case TOPIC_ALIAS:
                {
                    final Integer expectedValue = ((MqttProperties.IntegerProperty) expectedProperty).value;
                    final Integer actualValue = ((MqttProperties.IntegerProperty) actualProperty).value;
                    assertEquals("two byte property doesn't match", expectedValue, actualValue);
                    break;
                }
                // four byte value integer property
                case PUBLICATION_EXPIRY_INTERVAL:
                case SESSION_EXPIRY_INTERVAL:
                case WILL_DELAY_INTERVAL:
                case MAXIMUM_PACKET_SIZE:
                {
                    final Integer expectedValue = ((MqttProperties.IntegerProperty) expectedProperty).value;
                    final Integer actualValue = ((MqttProperties.IntegerProperty) actualProperty).value;
                    assertEquals("four byte property doesn't match", expectedValue, actualValue);
                    break;
                }
                // four byte value integer property
                case SUBSCRIPTION_IDENTIFIER:
                {
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
                case REASON_STRING:
                case USER_PROPERTY:
                {
                    final String expectedValue = ((MqttProperties.StringProperty) expectedProperty).value;
                    final String actualValue = ((MqttProperties.StringProperty) actualProperty).value;
                    assertEquals("String property doesn't match", expectedValue, actualValue);
                    break;
                }
                // byte[] property
                case CORRELATION_DATA:
                case AUTHENTICATION_DATA:
                {
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

    @Test
    public void testSubscribe() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(PAYLOAD_FORMAT_INDICATOR.value(), 6));
        final MqttSubscribeMessage message = MqttMessageBuilders.subscribe()
                .messageId((short) 1)
                .properties(props)
                .addSubscription("/topic", new SubscriptionOption(AT_LEAST_ONCE, true, true,
                        SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS))
                .build();
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());
        final MqttSubscribeMessage decodedMessage = (MqttSubscribeMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        final MqttMessageIdPlusPropertiesVariableHeader expectedHeader =
                (MqttMessageIdPlusPropertiesVariableHeader) message.variableHeader();
        final MqttMessageIdPlusPropertiesVariableHeader actualHeader =
                (MqttMessageIdPlusPropertiesVariableHeader) decodedMessage.variableHeader();
        validatePacketIdPlusPropertiesVariableHeader(expectedHeader, actualHeader);
        validateSubscribePayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testUnsubAck() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(PAYLOAD_FORMAT_INDICATOR.value(), 6));
        final MqttUnsubAckMessage message = MqttMessageBuilders.unsubAck()
                .packetId((short) 1)
                .properties(props)
                .addReasonCode((short) 0x83)
                .build();
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttUnsubAckMessage decodedMessage = (MqttUnsubAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePacketIdPlusPropertiesVariableHeader(
                (MqttMessageIdPlusPropertiesVariableHeader) message.variableHeader(),
                (MqttMessageIdPlusPropertiesVariableHeader) decodedMessage.variableHeader());
        assertEquals("Reason code list doesn't match", message.payload().unsubscribeReasonCodes(),
                decodedMessage.payload().unsubscribeReasonCodes());
    }

    @Test
    public void testDisconnect() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(SESSION_EXPIRY_INTERVAL.value(), 6));
        final MqttDisconnectMessage message = MqttMessageBuilders.disconnect()
                .reasonCode((short) 0x96) // Message rate too high
                .properties(props)
                .build();
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());
        final MqttDisconnectMessage decodedMessage = (MqttDisconnectMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        assertEquals("Reason code must match (0x96)",
                message.variableHeader().reasonCode(), decodedMessage.variableHeader().reasonCode());
        validateProperties(message.variableHeader().properties(), decodedMessage.variableHeader().properties());
    }

    @Test
    public void testAuth() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.BinaryProperty(AUTHENTICATION_DATA.value(), "secret".getBytes()));
        final MqttAuthMessage message = MqttMessageBuilders.auth()
                .reasonCode((short) 0x18) // Continue authentication
                .properties(props)
                .build();
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());
        final MqttAuthMessage decodedMessage = (MqttAuthMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        assertEquals("Reason code must match (0x18)",
                message.variableHeader().reasonCode(), decodedMessage.variableHeader().reasonCode());
        validateProperties(message.variableHeader().properties(), decodedMessage.variableHeader().properties());
    }
}
