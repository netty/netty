package io.netty.handler.codec.mqtt;

import io.netty.handler.codec.DecoderResult;

/**
 * Base class for MQTT v3 and v5 message factories.
 */
public abstract class AbstractMessageFactory {

    public abstract MqttMessage newMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader, Object payload);

    public static MqttMessage newInvalidMessage(Throwable cause) {
        return new MqttMessage(null, null, null, DecoderResult.failure(cause));
    }
}
