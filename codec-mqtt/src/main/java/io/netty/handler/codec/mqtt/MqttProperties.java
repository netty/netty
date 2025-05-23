/*
 * Copyright 2020 The Netty Project
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

import io.netty.util.collection.IntObjectHashMap;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * MQTT Properties container
 * */
public final class MqttProperties {

    // single byte properties
    static final int PAYLOAD_FORMAT_INDICATOR = 0x01;
    static final int REQUEST_PROBLEM_INFORMATION = 0x17;
    static final int REQUEST_RESPONSE_INFORMATION = 0x19;
    static final int MAXIMUM_QOS = 0x24;
    static final int RETAIN_AVAILABLE = 0x25;
    static final int WILDCARD_SUBSCRIPTION_AVAILABLE = 0x28;
    static final int SUBSCRIPTION_IDENTIFIER_AVAILABLE = 0x29;
    static final int SHARED_SUBSCRIPTION_AVAILABLE = 0x2A;

    // two bytes properties
    static final int SERVER_KEEP_ALIVE = 0x13;
    static final int RECEIVE_MAXIMUM = 0x21;
    static final int TOPIC_ALIAS_MAXIMUM = 0x22;
    static final int TOPIC_ALIAS = 0x23;

    // four bytes properties
    static final int PUBLICATION_EXPIRY_INTERVAL = 0x02;
    static final int SESSION_EXPIRY_INTERVAL = 0x11;
    static final int WILL_DELAY_INTERVAL = 0x18;
    static final int MAXIMUM_PACKET_SIZE = 0x27;

    // Variable Byte Integer
    static final int SUBSCRIPTION_IDENTIFIER = 0x0B;

    // UTF-8 Encoded String properties
    static final int CONTENT_TYPE = 0x03;
    static final int RESPONSE_TOPIC = 0x08;
    static final int ASSIGNED_CLIENT_IDENTIFIER = 0x12;
    static final int AUTHENTICATION_METHOD = 0x15;
    static final int RESPONSE_INFORMATION = 0x1A;
    static final int SERVER_REFERENCE = 0x1C;
    static final int REASON_STRING = 0x1F;
    static final int USER_PROPERTY = 0x26;

    // Binary Data
    static final int CORRELATION_DATA = 0x09;
    static final int AUTHENTICATION_DATA = 0x16;

    @Deprecated
    public enum MqttPropertyType {
        // single byte properties
        PAYLOAD_FORMAT_INDICATOR(MqttProperties.PAYLOAD_FORMAT_INDICATOR),
        REQUEST_PROBLEM_INFORMATION(MqttProperties.REQUEST_PROBLEM_INFORMATION),
        REQUEST_RESPONSE_INFORMATION(MqttProperties.REQUEST_RESPONSE_INFORMATION),
        MAXIMUM_QOS(MqttProperties.MAXIMUM_QOS),
        RETAIN_AVAILABLE(MqttProperties.RETAIN_AVAILABLE),
        WILDCARD_SUBSCRIPTION_AVAILABLE(MqttProperties.WILDCARD_SUBSCRIPTION_AVAILABLE),
        SUBSCRIPTION_IDENTIFIER_AVAILABLE(MqttProperties.SUBSCRIPTION_IDENTIFIER_AVAILABLE),
        SHARED_SUBSCRIPTION_AVAILABLE(MqttProperties.SHARED_SUBSCRIPTION_AVAILABLE),

        // two bytes properties
        SERVER_KEEP_ALIVE(MqttProperties.SERVER_KEEP_ALIVE),
        RECEIVE_MAXIMUM(MqttProperties.RECEIVE_MAXIMUM),
        TOPIC_ALIAS_MAXIMUM(MqttProperties.TOPIC_ALIAS_MAXIMUM),
        TOPIC_ALIAS(MqttProperties.TOPIC_ALIAS),

        // four bytes properties
        PUBLICATION_EXPIRY_INTERVAL(MqttProperties.PUBLICATION_EXPIRY_INTERVAL),
        SESSION_EXPIRY_INTERVAL(MqttProperties.SESSION_EXPIRY_INTERVAL),
        WILL_DELAY_INTERVAL(MqttProperties.WILL_DELAY_INTERVAL),
        MAXIMUM_PACKET_SIZE(MqttProperties.MAXIMUM_PACKET_SIZE),

        // Variable Byte Integer
        SUBSCRIPTION_IDENTIFIER(MqttProperties.SUBSCRIPTION_IDENTIFIER),

        // UTF-8 Encoded String properties
        CONTENT_TYPE(MqttProperties.CONTENT_TYPE),
        RESPONSE_TOPIC(MqttProperties.RESPONSE_TOPIC),
        ASSIGNED_CLIENT_IDENTIFIER(MqttProperties.ASSIGNED_CLIENT_IDENTIFIER),
        AUTHENTICATION_METHOD(MqttProperties.AUTHENTICATION_METHOD),
        RESPONSE_INFORMATION(MqttProperties.RESPONSE_INFORMATION),
        SERVER_REFERENCE(MqttProperties.SERVER_REFERENCE),
        REASON_STRING(MqttProperties.REASON_STRING),
        USER_PROPERTY(MqttProperties.USER_PROPERTY),

        // Binary Data
        CORRELATION_DATA(MqttProperties.CORRELATION_DATA),
        AUTHENTICATION_DATA(MqttProperties.AUTHENTICATION_DATA);

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
            return NO_PROPERTIES;
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

        @Override
        public int hashCode() {
            return propertyId + 31 * value.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            MqttProperty that = (MqttProperty) obj;
            return this.propertyId == that.propertyId && this.value.equals(that.value);
        }
    }

    public static final class IntegerProperty extends MqttProperty<Integer> {

        public IntegerProperty(int propertyId, Integer value) {
            super(propertyId, value);
        }

        @Override
        public String toString() {
            return "IntegerProperty(" + propertyId + ", " + value + ")";
        }
    }

    public static final class StringProperty extends MqttProperty<String> {

        public StringProperty(int propertyId, String value) {
            super(propertyId, value);
        }

        @Override
        public String toString() {
            return "StringProperty(" + propertyId + ", " + value + ")";
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
            super(USER_PROPERTY, new ArrayList<>());
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

        private static UserProperties fromUserPropertyCollection(Collection<UserProperty> properties) {
            UserProperties userProperties = new UserProperties();
            for (UserProperty property: properties) {
                userProperties.add(new StringPair(property.value.key, property.value.value));
            }
            return userProperties;
        }

        public void add(StringPair pair) {
            this.value.add(pair);
        }

        public void add(String key, String value) {
            this.value.add(new StringPair(key, value));
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("UserProperties(");
            boolean first = true;
            for (StringPair pair: value) {
                if (!first) {
                    builder.append(", ");
                }
                builder.append(pair.key + "->" + pair.value);
                first = false;
            }
            builder.append(")");
            return builder.toString();
        }
    }

    public static final class UserProperty extends MqttProperty<StringPair> {
        public UserProperty(String key, String value) {
            super(USER_PROPERTY, new StringPair(key, value));
        }

        @Override
        public String toString() {
            return "UserProperty(" + value.key + ", " + value.value + ")";
        }
    }

    public static final class BinaryProperty extends MqttProperty<byte[]> {

        public BinaryProperty(int propertyId, byte[] value) {
            super(propertyId, value);
        }

        @Override
        public String toString() {
            return "BinaryProperty(" + propertyId + ", " + value.length + " bytes)";
        }
    }

    public MqttProperties() {
        this(true);
    }

    private MqttProperties(boolean canModify) {
        this.canModify = canModify;
    }

    private IntObjectHashMap<MqttProperty> props;
    private List<UserProperty> userProperties;
    private List<IntegerProperty> subscriptionIds;
    private final boolean canModify;

    public void add(MqttProperty property) {
        if (!canModify) {
            throw new UnsupportedOperationException("adding property isn't allowed");
        }
        IntObjectHashMap<MqttProperty> props = this.props;
        if (property.propertyId == USER_PROPERTY) {
            List<UserProperty> userProperties = this.userProperties;
            if (userProperties == null) {
                userProperties = new ArrayList<>(1);
                this.userProperties = userProperties;
            }
            if (property instanceof UserProperty) {
                userProperties.add((UserProperty) property);
            } else if (property instanceof UserProperties) {
                for (StringPair pair: ((UserProperties) property).value) {
                    userProperties.add(new UserProperty(pair.key, pair.value));
                }
            } else {
                throw new IllegalArgumentException("User property must be of UserProperty or UserProperties type");
            }
        } else if (property.propertyId == SUBSCRIPTION_IDENTIFIER) {
            List<IntegerProperty> subscriptionIds = this.subscriptionIds;
            if (subscriptionIds == null) {
                subscriptionIds = new ArrayList<>(1);
                this.subscriptionIds = subscriptionIds;
            }
            if (property instanceof IntegerProperty) {
                subscriptionIds.add((IntegerProperty) property);
            } else {
                throw new IllegalArgumentException("Subscription ID must be an integer property");
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
        if (props == null && subscriptionIds == null && userProperties == null) {
            return Collections.emptyList();
        }
        if (subscriptionIds == null && userProperties == null) {
            return props.values();
        }
        if (props == null && userProperties == null) {
            return subscriptionIds;
        }
        List<MqttProperty> propValues = new ArrayList<MqttProperty>(props != null ? props.size() : 1);
        if (props != null) {
            propValues.addAll(props.values());
        }
        if (subscriptionIds != null) {
            propValues.addAll(subscriptionIds);
        }
        if (userProperties != null) {
            propValues.add(UserProperties.fromUserPropertyCollection(userProperties));
        }
        return propValues;
    }

    public boolean isEmpty() {
        IntObjectHashMap<MqttProperty> props = this.props;
        return props == null || props.isEmpty();
    }

    /**
     * Get property by ID. If there are multiple properties of this type (can be with Subscription ID)
     * then return the first one.
     *
     * @param propertyId ID of the property
     * @return a property if it is set, null otherwise
     */
    public MqttProperty getProperty(int propertyId) {
        if (propertyId == USER_PROPERTY) {
            //special handling to keep compatibility with earlier versions
            List<UserProperty> userProperties = this.userProperties;
            if (userProperties == null) {
                return null;
            }
            return UserProperties.fromUserPropertyCollection(userProperties);
        }
        if (propertyId == SUBSCRIPTION_IDENTIFIER) {
            List<IntegerProperty> subscriptionIds = this.subscriptionIds;
            if (subscriptionIds == null || subscriptionIds.isEmpty()) {
                return null;
            }
            return subscriptionIds.get(0);
        }
        IntObjectHashMap<MqttProperty> props = this.props;
        return props == null ? null : props.get(propertyId);
    }

    /**
     * Get properties by ID.
     * Some properties (Subscription ID and User Properties) may occur multiple times,
     * this method returns all their values in order.
     *
     * @param propertyId ID of the property
     * @return all properties having specified ID
     */
    public List<? extends MqttProperty> getProperties(int propertyId) {
        if (propertyId == USER_PROPERTY) {
            return userProperties == null ? Collections.emptyList() : userProperties;
        }
        if (propertyId == SUBSCRIPTION_IDENTIFIER) {
            return subscriptionIds == null ? Collections.emptyList() : subscriptionIds;
        }
        IntObjectHashMap<MqttProperty> props = this.props;
        return (props == null || !props.containsKey(propertyId)) ?
                Collections.emptyList() :
                Collections.singletonList(props.get(propertyId));
    }
}
