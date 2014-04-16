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

import io.netty.handler.codec.DecoderException;

public final class MqttValidationUtil {

    private static final char[] TOPIC_WILDCARDS = {'#', '+'};
    private static final int MAX_CLIENT_ID_LENGTH = 23;

    public static boolean isValidPublishTopicName(String topicName) {
        // publish topic name must not contain any wildcard
        for (char c : TOPIC_WILDCARDS) {
            if (topicName.indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidMessageId(int messageId) {
        return messageId != 0;
    }

    public static boolean isValidClientId(String clientId) {
        return clientId != null && clientId.length() <= MAX_CLIENT_ID_LENGTH;
    }

    public static MqttFixedHeader validateFixedHeader(MqttFixedHeader mqttFixedHeader) {
        switch (mqttFixedHeader.messageType()) {
            case PUBREL:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
                if (mqttFixedHeader.qosLevel() != QoS.AT_LEAST_ONCE) {
                    throw new DecoderException(String.format("%s message must have QoS 1",
                                               mqttFixedHeader.messageType().name()));
                }
            default:
                return mqttFixedHeader;
        }
    }

    private MqttValidationUtil() { }
}
