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
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.messages.ConnAckMessage;
import io.netty.handler.codec.mqtt.messages.ConnAckVariableHeader;
import io.netty.handler.codec.mqtt.messages.ConnectMessage;
import io.netty.handler.codec.mqtt.messages.ConnectPayload;
import io.netty.handler.codec.mqtt.messages.ConnectReturnCode;
import io.netty.handler.codec.mqtt.messages.ConnectVariableHeader;
import io.netty.handler.codec.mqtt.messages.FixedHeader;
import io.netty.handler.codec.mqtt.messages.Message;
import io.netty.handler.codec.mqtt.messages.MessageIdVariableHeader;
import io.netty.handler.codec.mqtt.messages.MessageType;
import io.netty.handler.codec.mqtt.messages.PublishMessage;
import io.netty.handler.codec.mqtt.messages.PublishVariableHeader;
import io.netty.handler.codec.mqtt.messages.SubAckMessage;
import io.netty.handler.codec.mqtt.messages.SubAckPayload;
import io.netty.handler.codec.mqtt.messages.SubscribeMessage;
import io.netty.handler.codec.mqtt.messages.SubscribePayload;
import io.netty.handler.codec.mqtt.messages.TopicSubscription;
import io.netty.handler.codec.mqtt.messages.UnsubscribeMessage;
import io.netty.handler.codec.mqtt.messages.UnsubscribePayload;
import io.netty.util.CharsetUtil;
import org.easymock.Mock;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.LinkedList;
import java.util.List;

import static io.netty.handler.codec.mqtt.MqttConstants.*;
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

    private static final int PROTOCOL_VERSION = 3;
    private static final int KEEP_ALIVE_SECONDS = 600;

    private static final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

    @Mock
    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

    @Mock
    private final Channel channel = mock(Channel.class);

    private final MqttDecoder mqttDecoder = new MqttDecoder();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(ctx.channel()).thenReturn(channel);
    }

    @Test
    public void testConnectMessage() throws Exception {
        final ConnectMessage message = createConnectMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object bout got " + out.size(), 1, out.size());

        final ConnectMessage decodedMessage = (ConnectMessage) out.get(0);

        validateFixedHeaders(message.getFixedHeader(), decodedMessage.getFixedHeader());
        vlidateConnectVariableHeader(message.getVariableHeader(), decodedMessage.getVariableHeader());
        validateConnectPayload(message.getPayload(), decodedMessage.getPayload());
    }

    @Test
    public void testConnAckMessage() throws Exception {
        final ConnAckMessage message = createConnAckMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object bout got " + out.size(), 1, out.size());

        final ConnAckMessage decodedMessage = (ConnAckMessage) out.get(0);
        validateFixedHeaders(message.getFixedHeader(), decodedMessage.getFixedHeader());
        validateConnAckVariableHeader(message.getVariableHeader(), decodedMessage.getVariableHeader());
    }

    @Test
    public void testPublishMessage() throws Exception {
        final PublishMessage message = createPublishMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object bout got " + out.size(), 1, out.size());

        final PublishMessage decodedMessage = (PublishMessage) out.get(0);
        validateFixedHeaders(message.getFixedHeader(), decodedMessage.getFixedHeader());
        validatePublishVariableHeader(message.getVariableHeader(), decodedMessage.getVariableHeader());
        validatePublishPayload(message.getPayload(), decodedMessage.getPayload());
    }

    @Test
    public void testPubAckMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MessageType.PUBACK);
    }

    @Test
    public void testPubRecMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MessageType.PUBREC);
    }

    @Test
    public void testPubRelMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MessageType.PUBREL);
    }

    @Test
    public void testPubCompMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MessageType.PUBCOMP);
    }

    @Test
    public void testSubscribeMessage() throws Exception {
        final SubscribeMessage message = createSubscribeMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object bout got " + out.size(), 1, out.size());

        final SubscribeMessage decodedMessage = (SubscribeMessage) out.get(0);
        validateFixedHeaders(message.getFixedHeader(), decodedMessage.getFixedHeader());
        validateMessageIdVariableHeader(message.getVariableHeader(), decodedMessage.getVariableHeader());
        validateSubscribePayload(message.getPayload(), decodedMessage.getPayload());
    }

    @Test
    public void testSubAckMessage() throws Exception {
        final SubAckMessage message = createSubAckMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object bout got " + out.size(), 1, out.size());

        final SubAckMessage decodedMessage = (SubAckMessage) out.get(0);
        validateFixedHeaders(message.getFixedHeader(), decodedMessage.getFixedHeader());
        validateMessageIdVariableHeader(message.getVariableHeader(), decodedMessage.getVariableHeader());
        validateSubAckPayload(message.getPayload(), decodedMessage.getPayload());
    }

    @Test
    public void testUnSubscribeMessage() throws Exception {
        final UnsubscribeMessage message = createUnsubscribeMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object bout got " + out.size(), 1, out.size());

        final UnsubscribeMessage decodedMessage = (UnsubscribeMessage) out.get(0);
        validateFixedHeaders(message.getFixedHeader(), decodedMessage.getFixedHeader());
        validateMessageIdVariableHeader(message.getVariableHeader(), decodedMessage.getVariableHeader());
        validateUnsubscribePayload(message.getPayload(), decodedMessage.getPayload());
    }

    @Test
    public void testUnsubAckMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MessageType.UNSUBACK);
    }

    @Test
    public void testPingReqMessage() throws Exception {
        testMessageWithOnlyFixedHeader(MessageType.PINGREQ);
    }

    @Test
    public void testPingRespMessage() throws Exception {
        testMessageWithOnlyFixedHeader(MessageType.PINGRESP);
    }

    @Test
    public void testDisconnectMessage() throws Exception {
        testMessageWithOnlyFixedHeader(MessageType.DISCONNECT);
    }

    private void testMessageWithOnlyFixedHeader(int messageType) throws Exception {
        Message message = createMessageWithFixedHeader(messageType);
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object bout got " + out.size(), 1, out.size());

        final Message decodedMessage = (Message) out.get(0);
        validateFixedHeaders(message.getFixedHeader(), decodedMessage.getFixedHeader());
    }

    private void testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(int messageType) throws Exception {
        Message message = createMessageWithFixedHeaderAndMessageIdVariableHeader(messageType);

        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object bout got " + out.size(), 1, out.size());

        final Message decodedMessage = (Message) out.get(0);
        validateFixedHeaders(message.getFixedHeader(), decodedMessage.getFixedHeader());
        validateMessageIdVariableHeader(
                (MessageIdVariableHeader) message.getVariableHeader(),
                (MessageIdVariableHeader) decodedMessage.getVariableHeader());
    }

    // Factory methods of different MQTT
    // Message types to help testing

    private static Message createMessageWithFixedHeader(int messageType) {
        return new Message(new FixedHeader(messageType, false, QOS0, false, 0));
    }

    private static Message createMessageWithFixedHeaderAndMessageIdVariableHeader(int messageType) {
        FixedHeader fixedHeader = new FixedHeader(messageType, false, QOS1, false, 0);
        MessageIdVariableHeader messageIdVariableHeader = new MessageIdVariableHeader(12345);
        return new Message(fixedHeader, messageIdVariableHeader);
    }

    private static ConnectMessage createConnectMessage() {
        FixedHeader fixedHeader = new FixedHeader(MessageType.CONNECT, false, QOS0, false, 0);
        ConnectVariableHeader connectVariableHeader =
                new ConnectVariableHeader(
                        PROTOCOL_NAME,
                        PROTOCOL_VERSION,
                        true,
                        true,
                        true,
                        1,
                        true,
                        true,
                        KEEP_ALIVE_SECONDS);
        ConnectPayload connectPayload = new ConnectPayload(CLIENT_ID, WILL_TOPIC, WILL_MESSAGE, USER_NAME, PASSWORD);

        return new ConnectMessage(fixedHeader, connectVariableHeader, connectPayload);
    }

    private static ConnAckMessage createConnAckMessage() {
        FixedHeader fixedHeader = new FixedHeader(MessageType.CONNACK, false, QOS0, false, 0);
        ConnAckVariableHeader connAckVariableHeader = new ConnAckVariableHeader(ConnectReturnCode.CONNECTION_ACCEPTED);
        return new ConnAckMessage(fixedHeader, connAckVariableHeader);
    }

    private static PublishMessage createPublishMessage() {
        FixedHeader fixedHeader = new FixedHeader(MessageType.PUBLISH, false, QOS1, true, 0);
        PublishVariableHeader publishVariableHeader = new PublishVariableHeader("/abc", 1234);
        ByteBuf payload =  ALLOCATOR.heapBuffer(8);
        payload.writeBytes("whatever".getBytes(CharsetUtil.UTF_8));
        return new PublishMessage(fixedHeader, publishVariableHeader, payload);
    }

    private static SubscribeMessage createSubscribeMessage() {
        FixedHeader fixedHeader = new FixedHeader(MessageType.SUBSCRIBE, false, QOS1, true, 0);
        MessageIdVariableHeader messageIdVariableHeader = new MessageIdVariableHeader(12345);

        List<TopicSubscription> topicSubscriptions = new LinkedList<TopicSubscription>();
        topicSubscriptions.add(new TopicSubscription("/abc", QOS1));
        topicSubscriptions.add(new TopicSubscription("/def", QOS1));
        topicSubscriptions.add(new TopicSubscription("/xyz", QOS2));

        SubscribePayload subscribePayload = new SubscribePayload(topicSubscriptions);
        return new SubscribeMessage(fixedHeader, messageIdVariableHeader, subscribePayload);
    }

    private static SubAckMessage createSubAckMessage() {
        FixedHeader fixedHeader = new FixedHeader(MessageType.SUBACK, false, QOS1, false, 0);
        MessageIdVariableHeader messageIdVariableHeader = new MessageIdVariableHeader(12345);
        List<Integer> grantedQosLevels = new LinkedList<Integer>();
        grantedQosLevels.add(1);
        grantedQosLevels.add(2);
        grantedQosLevels.add(0);
        SubAckPayload subAckPayload = new SubAckPayload(grantedQosLevels);
        return new SubAckMessage(fixedHeader, messageIdVariableHeader, subAckPayload);
    }

    private static UnsubscribeMessage createUnsubscribeMessage() {
        FixedHeader fixedHeader = new FixedHeader(MessageType.UNSUBSCRIBE, false, QOS1, true, 0);
        MessageIdVariableHeader messageIdVariableHeader = new MessageIdVariableHeader(12345);

        List<String> topics = new LinkedList<String>();
        topics.add("/abc");
        topics.add("/def");
        topics.add("/xyz");

        UnsubscribePayload unsubscribePayload = new UnsubscribePayload(topics);
        return new UnsubscribeMessage(fixedHeader, messageIdVariableHeader, unsubscribePayload);
    }

    // Helper methdos to compare expected and actual
    // MQTT messages

    private static void validateFixedHeaders(FixedHeader expected, FixedHeader actual) {
        assertEquals("FixedHeader MessageType mismatch ", expected.getMessageType(), actual.getMessageType());
        assertEquals("FixedHeader Qos mismatch ", expected.getQosLevel(), actual.getQosLevel());
    }

    private static void vlidateConnectVariableHeader(ConnectVariableHeader expected, ConnectVariableHeader actual) {
        assertEquals("ConnectVariableHeader Name mismatch ", expected.getName(), actual.getName());
        assertEquals(
                "ConnectVariableHeader KeepAliveTimeSeconds mismatch ",
                expected.getKeepAliveTimeSeconds(),
                actual.getKeepAliveTimeSeconds());
        assertEquals("ConnectVariableHeader Version mismatch ", expected.getVersion(), actual.getVersion());
        assertEquals("ConnectVariableHeader WillQos mismatch ", expected.getWillQos(), actual.getWillQos());

        assertEquals("ConnectVariableHeader HasUserName mismatch ", expected.hasUserName(), actual.hasUserName());
        assertEquals("ConnectVariableHeader HasPassword mismatch ", expected.hasPassword(), actual.hasPassword());
        assertEquals(
                "ConnectVariableHeader IsCleanSession mismatch ",
                expected.isCleanSession(),
                actual.isCleanSession());
        assertEquals("ConnectVariableHeader IsWillFlag mismatch ", expected.isWillFlag(), actual.isWillFlag());
        assertEquals("ConnectVariableHeader IsWillRetain mismatch ", expected.isWillRetain(), actual.isWillRetain());
    }

    private static void validateConnectPayload(ConnectPayload expected, ConnectPayload actual) {
        assertEquals(
                "ConnectPayload ClientIdentifier mismatch ",
                expected.getClientIdentifier(),
                actual.getClientIdentifier());
        assertEquals("ConnectPayload UserName mismatch ", expected.getUserName(), actual.getUserName());
        assertEquals("ConnectPayload Password mismatch ", expected.getPassword(), actual.getPassword());
        assertEquals("ConnectPayload WillMessage mismatch ", expected.getWillMessage(), actual.getWillMessage());
        assertEquals("ConnectPayload WillTopic mismatch ", expected.getWillTopic(), actual.getWillTopic());
    }

    private static void validateConnAckVariableHeader(ConnAckVariableHeader expected, ConnAckVariableHeader actual) {
        assertEquals(
                "ConnAckVariableHeader ConnectReturnCode mismatch",
                expected.getConnectReturnCode(),
                actual.getConnectReturnCode());
    }

    private static void validatePublishVariableHeader(PublishVariableHeader expected, PublishVariableHeader actual) {
        assertEquals("PublishVariableHeader TopicName mismatch ", expected.getTopicName(), actual.getTopicName());
        assertEquals("PublishVariableHeader MessageId mismatch ", expected.getMessageId(), actual.getMessageId());
    }

    private static void validatePublishPayload(ByteBuf expected, ByteBuf actual) {
        assertArrayEquals("PublishPayload mismatch", expected.array(), actual.array());
    }

    private static void validateMessageIdVariableHeader(
            MessageIdVariableHeader expected,
            MessageIdVariableHeader actual) {
        assertEquals("MessageIdVariableHeader MessageId mismatch ", expected.getMessageId(), actual.getMessageId());
    }

    private static void validateSubscribePayload(SubscribePayload expected, SubscribePayload actual) {
        List<TopicSubscription> expectedTopicSubscriptions = expected.getTopicSubscriptionList();
        List<TopicSubscription> actualTopicSubscriptions = actual.getTopicSubscriptionList();

        assertEquals(
                "SubscribePayload TopicSubscriptionList size mismatch ",
                expectedTopicSubscriptions.size(),
                actualTopicSubscriptions.size());
        for (int i = 0; i < expectedTopicSubscriptions.size(); i++) {
            validateTopicSubscription(expectedTopicSubscriptions.get(i), actualTopicSubscriptions.get(i));
        }
    }

    private static void validateTopicSubscription(TopicSubscription expected, TopicSubscription actual) {
        assertEquals("TopicSubscription TopicName mismatch ", expected.getTopicName(), actual.getTopicName());
        assertEquals("TopicSubscription Qos mismatch ", expected.getQualityOfService(), actual.getQualityOfService());
    }

    private static void validateSubAckPayload(SubAckPayload expected, SubAckPayload actual) {
        assertArrayEquals(
                "SubAckPayload GrantedQosLevels mismatch ",
                expected.getGrantedQoSLevels().toArray(),
                actual.getGrantedQoSLevels().toArray());
    }

    private static void validateUnsubscribePayload(UnsubscribePayload expected, UnsubscribePayload actual) {
        assertArrayEquals(
                "UnsubscribePayload TopicList mismatch ",
                expected.getTopics().toArray(),
                actual.getTopics().toArray());
    }
}
