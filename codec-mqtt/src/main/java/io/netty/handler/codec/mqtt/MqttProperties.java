package io.netty.handler.codec.mqtt;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * MQTT Properties container
 * */
public class MqttProperties {

    public static final MqttProperties NO_PROPERTIES = new MqttProperties();

    public static abstract class MqttProperty<T> {
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
