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

import io.netty.handler.codec.mqtt.messages.ConnAckMessage;
import io.netty.handler.codec.mqtt.messages.ConnAckVariableHeader;
import io.netty.handler.codec.mqtt.messages.ConnectMessage;
import io.netty.handler.codec.mqtt.messages.ConnectPayload;
import io.netty.handler.codec.mqtt.messages.ConnectVariableHeader;
import io.netty.handler.codec.mqtt.messages.FixedHeader;
import io.netty.handler.codec.mqtt.messages.Message;
import io.netty.handler.codec.mqtt.messages.MessageIdVariableHeader;
import io.netty.handler.codec.mqtt.messages.MessageType;
import io.netty.handler.codec.mqtt.messages.PubAckMessage;
import io.netty.handler.codec.mqtt.messages.PublishMessage;
import io.netty.handler.codec.mqtt.messages.PublishVariableHeader;
import io.netty.handler.codec.mqtt.messages.SubAckMessage;
import io.netty.handler.codec.mqtt.messages.SubAckPayload;
import io.netty.handler.codec.mqtt.messages.SubscribeMessage;
import io.netty.handler.codec.mqtt.messages.SubscribePayload;
import io.netty.handler.codec.mqtt.messages.UnsubAckMessage;
import io.netty.handler.codec.mqtt.messages.UnsubscribeMessage;
import io.netty.handler.codec.mqtt.messages.UnsubscribePayload;

/**
 * Utility class with factory methods to create different types of MQTT messages.
 */
public final class MessageFactory {

    public static Message create(FixedHeader fixedHeader, Object variableHeader, Object payload) {
        switch (fixedHeader.getMessageType()) {
            case MessageType.CONNECT :
                return new ConnectMessage(
                        fixedHeader,
                        (ConnectVariableHeader) variableHeader,
                        (ConnectPayload) payload);

            case MessageType.CONNACK:
                return new ConnAckMessage(fixedHeader, (ConnAckVariableHeader) variableHeader);

            case MessageType.SUBSCRIBE:
                return new SubscribeMessage(
                        fixedHeader,
                        (MessageIdVariableHeader) variableHeader,
                        (SubscribePayload) payload);

            case MessageType.SUBACK:
                return new SubAckMessage(
                        fixedHeader,
                        (MessageIdVariableHeader) variableHeader,
                        (SubAckPayload) payload);

            case MessageType.UNSUBACK:
                return new UnsubAckMessage(
                        fixedHeader,
                        (MessageIdVariableHeader) variableHeader);

            case MessageType.UNSUBSCRIBE:
                return new UnsubscribeMessage(
                        fixedHeader,
                        (MessageIdVariableHeader) variableHeader,
                        (UnsubscribePayload) payload);

            case MessageType.PUBLISH:
                return new PublishMessage(
                        fixedHeader,
                        (PublishVariableHeader) variableHeader,
                        (byte[]) payload);

            case MessageType.PUBACK:
            case MessageType.PUBREC:
            case MessageType.PUBREL:
            case MessageType.PUBCOMP:
                return new PubAckMessage(fixedHeader, (MessageIdVariableHeader) variableHeader);

            case MessageType.PINGREQ:
            case MessageType.PINGRESP:
            case MessageType.DISCONNECT:
                return new Message(fixedHeader);

            default:
                throw new IllegalArgumentException("Unknown message type: " + fixedHeader.getMessageType());
        }
    }

    private MessageFactory() { }
}
