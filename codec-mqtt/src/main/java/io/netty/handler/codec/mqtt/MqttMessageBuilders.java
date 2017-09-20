/*
 * Copyright 2017 The Netty Project
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
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.List;

public final class MqttMessageBuilders {

    public static final class PublishBuilder {
        private String topic;
        private boolean retained;
        private MqttQoS qos;
        private ByteBuf payload;
        private int messageId;

        PublishBuilder() {
        }

        public PublishBuilder topicName(String topic) {
            this.topic = topic;
            return this;
        }

        public PublishBuilder retained(boolean retained) {
            this.retained = retained;
            return this;
        }

        public PublishBuilder qos(MqttQoS qos) {
            this.qos = qos;
            return this;
        }

        public PublishBuilder payload(ByteBuf payload) {
            this.payload = payload;
            return this;
        }

        public PublishBuilder messageId(int messageId) {
            this.messageId = messageId;
            return this;
        }

        public MqttPublishMessage build() {
            MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, retained, 0);
            MqttPublishVariableHeader mqttVariableHeader = new MqttPublishVariableHeader(topic, messageId);
            return new MqttPublishMessage(mqttFixedHeader, mqttVariableHeader, Unpooled.buffer().writeBytes(payload));
        }
    }

    public static final class ConnectBuilder {

        private MqttVersion version = MqttVersion.MQTT_3_1_1;
        private String clientId;
        private boolean cleanSession;
        private boolean hasUser;
        private boolean hasPassword;
        private int keepAliveSecs;
        private boolean willFlag;
        private boolean willRetain;
        private MqttQoS willQos = MqttQoS.AT_MOST_ONCE;
        private String willTopic;
        private byte[] willMessage;
        private String username;
        private byte[] password;

        ConnectBuilder() {
        }

        public ConnectBuilder protocolVersion(MqttVersion version) {
            this.version = version;
            return this;
        }

        public ConnectBuilder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public ConnectBuilder cleanSession(boolean cleanSession) {
            this.cleanSession = cleanSession;
            return this;
        }

        public ConnectBuilder keepAlive(int keepAliveSecs) {
            this.keepAliveSecs = keepAliveSecs;
            return this;
        }

        public ConnectBuilder willFlag(boolean willFlag) {
            this.willFlag = willFlag;
            return this;
        }

        public ConnectBuilder willQoS(MqttQoS willQos) {
            this.willQos = willQos;
            return this;
        }

        public ConnectBuilder willTopic(String willTopic) {
            this.willTopic = willTopic;
            return this;
        }

        /**
         * @deprecated use {@link ConnectBuilder#willMessage(byte[])} instead
         */
        @Deprecated
        public ConnectBuilder willMessage(String willMessage) {
            willMessage(willMessage == null ? null : willMessage.getBytes(CharsetUtil.UTF_8));
            return this;
        }

        public ConnectBuilder willMessage(byte[] willMessage) {
            this.willMessage = willMessage;
            return this;
        }

        public ConnectBuilder willRetain(boolean willRetain) {
            this.willRetain = willRetain;
            return this;
        }

        public ConnectBuilder hasUser(boolean value) {
            this.hasUser = value;
            return this;
        }

        public ConnectBuilder hasPassword(boolean value) {
            this.hasPassword = value;
            return this;
        }

        public ConnectBuilder username(String username) {
            this.hasUser = username != null;
            this.username = username;
            return this;
        }

        /**
         * @deprecated use {@link ConnectBuilder#password(byte[])} instead
         */
        @Deprecated
        public ConnectBuilder password(String password) {
            password(password == null ? null : password.getBytes(CharsetUtil.UTF_8));
            return this;
        }

        public ConnectBuilder password(byte[] password) {
            this.hasPassword = password != null;
            this.password = password;
            return this;
        }

        public MqttConnectMessage build() {
            MqttFixedHeader mqttFixedHeader =
                    new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);
            MqttConnectVariableHeader mqttConnectVariableHeader =
                    new MqttConnectVariableHeader(
                            version.protocolName(),
                            version.protocolLevel(),
                            hasUser,
                            hasPassword,
                            willRetain,
                            willQos.value(),
                            willFlag,
                            cleanSession,
                            keepAliveSecs);
            MqttConnectPayload mqttConnectPayload =
                    new MqttConnectPayload(clientId, willTopic, willMessage, username, password);
            return new MqttConnectMessage(mqttFixedHeader, mqttConnectVariableHeader, mqttConnectPayload);
        }
    }

    public static final class SubscribeBuilder {

        private List<MqttTopicSubscription> subscriptions;
        private int messageId;

        SubscribeBuilder() {
        }

        public SubscribeBuilder addSubscription(MqttQoS qos, String topic) {
            if (subscriptions == null) {
                subscriptions = new ArrayList<MqttTopicSubscription>(5);
            }
            subscriptions.add(new MqttTopicSubscription(topic, qos));
            return this;
        }

        public SubscribeBuilder messageId(int messageId) {
            this.messageId = messageId;
            return this;
        }

        public MqttSubscribeMessage build() {
            MqttFixedHeader mqttFixedHeader =
                    new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0);
            MqttMessageIdVariableHeader mqttVariableHeader = MqttMessageIdVariableHeader.from(messageId);
            MqttSubscribePayload mqttSubscribePayload = new MqttSubscribePayload(subscriptions);
            return new MqttSubscribeMessage(mqttFixedHeader, mqttVariableHeader, mqttSubscribePayload);
        }
    }

    public static final class UnsubscribeBuilder {

        private List<String> topicFilters;
        private int messageId;

        UnsubscribeBuilder() {
        }

        public UnsubscribeBuilder addTopicFilter(String topic) {
            if (topicFilters == null) {
                topicFilters = new ArrayList<String>(5);
            }
            topicFilters.add(topic);
            return this;
        }

        public UnsubscribeBuilder messageId(int messageId) {
            this.messageId = messageId;
            return this;
        }

        public MqttUnsubscribeMessage build() {
            MqttFixedHeader mqttFixedHeader =
                    new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0);
            MqttMessageIdVariableHeader mqttVariableHeader = MqttMessageIdVariableHeader.from(messageId);
            MqttUnsubscribePayload mqttSubscribePayload = new MqttUnsubscribePayload(topicFilters);
            return new MqttUnsubscribeMessage(mqttFixedHeader, mqttVariableHeader, mqttSubscribePayload);
        }
    }

    public static final class ConnAckBuilder {

        private MqttConnectReturnCode returnCode;
        private boolean sessionPresent;

        ConnAckBuilder() {
        }

        public ConnAckBuilder returnCode(MqttConnectReturnCode returnCode) {
            this.returnCode = returnCode;
            return this;
        }

        public ConnAckBuilder sessionPresent(boolean sessionPresent) {
            this.sessionPresent = sessionPresent;
            return this;
        }

        public MqttConnAckMessage build() {
            MqttFixedHeader mqttFixedHeader =
                    new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
            MqttConnAckVariableHeader mqttConnAckVariableHeader =
                    new MqttConnAckVariableHeader(returnCode, sessionPresent);
            return new MqttConnAckMessage(mqttFixedHeader, mqttConnAckVariableHeader);
        }
    }

    public static ConnectBuilder connect() {
        return new ConnectBuilder();
    }

    public static ConnAckBuilder connAck() {
        return new ConnAckBuilder();
    }

    public static PublishBuilder publish() {
        return new PublishBuilder();
    }

    public static SubscribeBuilder subscribe() {
        return new SubscribeBuilder();
    }

    public static UnsubscribeBuilder unsubscribe() {
        return new UnsubscribeBuilder();
    }

    private MqttMessageBuilders() {
    }
}
