package io.netty.handler.codec.mqtt;

/**
 * MQTT Disconnect message
 */
public final class MqttDisconnectMessage extends MqttMessage {

    public MqttDisconnectMessage(
            MqttFixedHeader mqttFixedHeader,
            MqttDisconnectVariableHeader variableHeader) {
        super(mqttFixedHeader, variableHeader);
    }

    @Override
    public MqttDisconnectVariableHeader variableHeader() {
        return (MqttDisconnectVariableHeader) super.variableHeader();
    }

}