package io.netty.handler.codec.mqtt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MqttReasonCodesTest {

    @Test
    public void givenADisconnectReasonCodeTheCorrectEnumerationValueIsReturned() {
        assertEquals(MqttReasonCodes.Disconnect.WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED, MqttReasonCodes.Disconnect.valueOf((byte) 0xA2),
                "0xA2 must match 'wildcard subscriptions not supported'");
    }
}