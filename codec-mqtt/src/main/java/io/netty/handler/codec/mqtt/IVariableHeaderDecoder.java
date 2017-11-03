package io.netty.handler.codec.mqtt;

import io.netty.buffer.ByteBuf;

/**
 * Used to decode only the Variable Header part of MQTT packets, which is what differs between v3.1.1 and v5
 */
interface IVariableHeaderDecoder {

    MqttDecoder.Result<?> decodeVariableHeader(ByteBuf buffer, MqttFixedHeader mqttFixedHeader);
}
