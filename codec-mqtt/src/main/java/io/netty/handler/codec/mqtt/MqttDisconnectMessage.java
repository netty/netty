package io.netty.handler.codec.mqtt;

/**
 * MQTT Disconnect message
 */
public final class MqttDisconnectMessage extends MqttMessage {

    public MqttDisconnectMessage(
            MqttFixedHeader mqttFixedHeader,
            MqttReasonCodePlusPropertiesVariableHeader variableHeader) {
        super(mqttFixedHeader, variableHeader);
    }

    @Override
    public MqttReasonCodePlusPropertiesVariableHeader variableHeader() {
        return (MqttReasonCodePlusPropertiesVariableHeader) super.variableHeader();
    }

}