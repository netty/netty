/*
 * Copyright 2020 The Netty Project
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

import io.netty.util.collection.IntObjectHashMap;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * MQTT Properties container
 * */
public final class MqttProperties {

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

        private static final MqttPropertyType[] VALUES;

        static {
            VALUES = new MqttPropertyType[43];
            for (MqttPropertyType v : values()) {
                VALUES[v.value] = v;
            }
        }

        private final int value;

        MqttPropertyType(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static MqttPropertyType valueOf(int type) {
            MqttPropertyType t = null;
            try {
                t = VALUES[type];
            } catch (ArrayIndexOutOfBoundsException ignored) {
                // nop
            }
            if (t == null) {
                throw new IllegalArgumentException("unknown property type: " + type);
            }
            return t;
        }
    }

    public static final MqttProperties NO_PROPERTIES = new MqttProperties(false);

    static MqttProperties withEmptyDefaults(MqttProperties properties) {
        if (properties == null) {
            return MqttProperties.NO_PROPERTIES;
        }
        return properties;
    }

    /**
     * MQTT property base class
     *
     * @param <T> property type
     */
    public abstract static class MqttProperty<T> {
        final T value;
        final int propertyId;

        protected MqttProperty(int propertyId, T value) {
            this.propertyId = propertyId;
            this.value = value;
        }

        /**
         * Get MQTT property value
         *
         * @return property value
         */
        public T value() {
            return value;
        }

        /**
         * Get MQTT property ID
         * @return property ID
         */
        public int propertyId() {
            return propertyId;
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

    public static final class StringPair {
        public final String key;
        public final String value;

        public StringPair(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public int hashCode() {
            return key.hashCode() + 31 * value.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            StringPair that = (StringPair) obj;

            return that.key.equals(this.key) && that.value.equals(this.value);
        }
    }

    //User properties are the only properties that may be included multiple times and
    //are the only properties where ordering is required. Therefore, they need a special handling
    public static final class UserProperties extends MqttProperty<List<StringPair>> {
        public UserProperties() {
            super(MqttPropertyType.USER_PROPERTY.value, new ArrayList<StringPair>());
        }

        /**
         * Create user properties from the collection of the String pair values
         *
         * @param values string pairs. Collection entries are copied, collection itself isn't shared
         */
        public UserProperties(Collection<StringPair> values) {
            this();
            this.value.addAll(values);
        }

        public void add(StringPair pair) {
            this.value.add(pair);
        }

        public void add(String key, String value) {
            this.value.add(new StringPair(key, value));
        }
    }

    public static final class UserProperty extends MqttProperty<StringPair> {
        public UserProperty(String key, String value) {
            super(MqttPropertyType.USER_PROPERTY.value, new StringPair(key, value));
        }
    }

    public static final class BinaryProperty extends MqttProperty<byte[]> {

        public BinaryProperty(int propertyId, byte[] value) {
            super(propertyId, value);
        }
    }

    public MqttProperties() {
        this(true);
    }

    private MqttProperties(boolean canModify) {
        this.canModify = canModify;
    }

    private IntObjectHashMap<MqttProperty> props;
    private final boolean canModify;

    public void add(MqttProperty property) {
        if (!canModify) {
            throw new UnsupportedOperationException("adding property isn't allowed");
        }
        IntObjectHashMap<MqttProperty> props = this.props;
        if (property.propertyId == MqttPropertyType.USER_PROPERTY.value) {
            UserProperties userProps = (UserProperties) (props != null? props.get(property.propertyId) : null);
            if (userProps == null) {
                userProps = new UserProperties();
                if (props == null) {
                    props = new IntObjectHashMap<MqttProperty>();
                    this.props = props;
                }
                props.put(property.propertyId, userProps);
            }
            if (property instanceof UserProperty) {
                userProps.add(((UserProperty) property).value);
            } else {
                for (StringPair pair: ((UserProperties) property).value) {
                    userProps.add(pair);
                }
            }
        } else {
            if (props == null) {
                props = new IntObjectHashMap<MqttProperty>();
                this.props = props;
            }
            props.put(property.propertyId, property);
        }
    }

    public Collection<? extends MqttProperty> listAll() {
        IntObjectHashMap<MqttProperty> props = this.props;
        return props == null? Collections.<MqttProperty>emptyList() : props.values();
    }

    public boolean isEmpty() {
        IntObjectHashMap<MqttProperty> props = this.props;
        return props == null || props.isEmpty();
    }

    public MqttProperty getProperty(int propertyId) {
        IntObjectHashMap<MqttProperty> props = this.props;
        return props == null? null : props.get(propertyId);
    }
}
