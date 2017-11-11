package io.netty.handler.codec.mqtt;

/**
 * MQTT Auth message
 */
public final class MqttAuthMessage extends MqttMessage {

    public MqttAuthMessage(
            MqttFixedHeader mqttFixedHeader,
            MqttReasonCodePlusPropertiesVariableHeader variableHeader) {
        super(mqttFixedHeader, variableHeader);
    }

    @Override
    public MqttReasonCodePlusPropertiesVariableHeader variableHeader() {
        return (MqttReasonCodePlusPropertiesVariableHeader) super.variableHeader();
    }

}