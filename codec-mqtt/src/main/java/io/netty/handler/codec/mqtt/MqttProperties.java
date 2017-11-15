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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * MQTT Properties container
 * */
public class MqttProperties {

    public enum MqttPropertyType {

        // single byte properties
        PAYLOAD_FORMAT_INDICATOR(0x01),
        REQUEST_PROBLEM_INFORMATION(0x17),
        REQUEST_RESPONSE_INFORMATION(0x19),
        MAXIMUM_QOS(0x24),
        RETAIN_AVAILABLE(0x25),
        WILDCARD_SUBSCRIPTION_AVAILABLE(0x28),
        SUBSCRIPTION_IDENTIFIER_AVAILABLE(0x29),
        SHARED_SUBSCRIPTION_AVAILABLE(0x2A),

        // two bytes properties
        SERVER_KEEP_ALIVE(0x13),
        RECEIVE_MAXIMUM(0x21),
        TOPIC_ALIAS_MAXIMUM(0x22),
        TOPIC_ALIAS(0x23),

        // four bytes properties
        PUBLICATION_EXPIRY_INTERVAL(0x02),
        SESSION_EXPIRY_INTERVAL(0x11),
        WILL_DELAY_INTERVAL(0x18),
        MAXIMUM_PACKET_SIZE(0x27),

        // Variable Byte Integer
        SUBSCRIPTION_IDENTIFIER(0x0B),

        // UTF-8 Encoded String properties
        CONTENT_TYPE(0x03),
        RESPONSE_TOPIC(0x08),
        ASSIGNED_CLIENT_IDENTIFIER(0x12),
        AUTHENTICATION_METHOD(0x15),
        RESPONSE_INFORMATION(0x1A),
        SERVER_REFERENCE(0x1C),
        REASON_STRING(0x1F),
        USER_PROPERTY(0x26),

        // Binary Data
        CORRELATION_DATA(0x09),
        AUTHENTICATION_DATA(0x16);

        private final int value;

        MqttPropertyType(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static MqttPropertyType valueOf(int type) {
            for (MqttPropertyType t : values()) {
                if (t.value == type) {
                    return t;
                }
            }
            throw new IllegalArgumentException("unknown property type: " + type);
        }
    }

    public static final MqttProperties NO_PROPERTIES = new MqttProperties();

    public abstract static class MqttProperty<T> {
        final T value;
        final int propertyId;

        public MqttProperty(int propertyId, T value) {
            this.propertyId = propertyId;
            this.value = value;
        }
    }

    public static final class IntegerProperty extends MqttProperty<Integer> {

        public IntegerProperty(int propertyId, Integer value) {
            super(propertyId, value);
        }
    }

    public static final class StringProperty extends MqttProperty<String> {

        public StringProperty(int propertyId, String value) {
            super(propertyId, value);
        }
    }

    public static final class BinaryProperty extends MqttProperty<byte[]> {

        public BinaryProperty(int propertyId, byte[] value) {
            super(propertyId, value);
        }
    }

    private final Map<Integer, MqttProperty> props = new HashMap<Integer, MqttProperty>();

    public void add(MqttProperty property) {
        props.put(property.propertyId, property);
    }

    public Collection<MqttProperty> listAll() {
        return props.values();
    }

    public MqttProperty getProperty(int propertyId) {
        return props.get(propertyId);
    }
}
