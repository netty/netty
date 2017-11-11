package io.netty.handler.codec.mqtt;

import io.netty.util.internal.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Payload for MQTT unsuback message as in V5.
 */
public class MqttUnsubAckPayload {

    private final List<Short> unsubscribeReasonCodes;


    public MqttUnsubAckPayload(short... unsubscribeReasonCodes) {
        if (unsubscribeReasonCodes == null) {
            throw new NullPointerException("unsubscribeReasonCodes");
        }

        List<Short> list = new ArrayList<Short>(unsubscribeReasonCodes.length);
        for (Short v: unsubscribeReasonCodes) {
            list.add(v);
        }
        this.unsubscribeReasonCodes = Collections.unmodifiableList(list);
    }

    public MqttUnsubAckPayload(Iterable<Short> unsubscribeReasonCodes) {
        if (unsubscribeReasonCodes == null) {
            throw new NullPointerException("unsubscribeReasonCodes");
        }
        List<Short> list = new ArrayList<Short>();
        for (Short v: unsubscribeReasonCodes) {
            if (v == null) {
                break;
            }
            list.add(v);
        }
        this.unsubscribeReasonCodes = Collections.unmodifiableList(list);
    }

    public List<Short> unsubscribeReasonCodes() {
        return unsubscribeReasonCodes;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append("unsubscribeReasonCodes=").append(unsubscribeReasonCodes)
                .append(']')
                .toString();
    }
}
