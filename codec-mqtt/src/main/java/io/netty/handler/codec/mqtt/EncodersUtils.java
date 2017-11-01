package io.netty.handler.codec.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

/**
 * Shared static utils methods between MqttEncoders v3.1 and v5
 */
final class EncodersUtils {

    static int getConnVariableHeaderFlag(MqttConnectVariableHeader variableHeader) {
        int flagByte = 0;
        if (variableHeader.hasUserName()) {
            flagByte |= 0x80;
        }
        if (variableHeader.hasPassword()) {
            flagByte |= 0x40;
        }
        if (variableHeader.isWillRetain()) {
            flagByte |= 0x20;
        }
        flagByte |= (variableHeader.willQos() & 0x03) << 3;
        if (variableHeader.isWillFlag()) {
            flagByte |= 0x04;
        }
        if (variableHeader.isCleanSession()) {
            flagByte |= 0x02;
        }
        return flagByte;
    }

    static void writeUTF8String(ByteBuf buf, String s) {
        byte[] sBytes = encodeStringUtf8(s);
        buf.writeShort(sBytes.length);
        buf.writeBytes(sBytes, 0, sBytes.length);
    }

    static int getFixedHeaderByte1(MqttFixedHeader header) {
        int ret = 0;
        ret |= header.messageType().value() << 4;
        if (header.isDup()) {
            ret |= 0x08;
        }
        ret |= header.qosLevel().value() << 1;
        if (header.isRetain()) {
            ret |= 0x01;
        }
        return ret;
    }

    static void writeVariableLengthInt(ByteBuf buf, int num) {
        do {
            int digit = num % 128;
            num /= 128;
            if (num > 0) {
                digit |= 0x80;
            }
            buf.writeByte(digit);
        } while (num > 0);
    }

    static int getVariableLengthInt(int num) {
        int count = 0;
        do {
            num /= 128;
            count++;
        } while (num > 0);
        return count;
    }

    static byte[] encodeStringUtf8(String s) {
        return s.getBytes(CharsetUtil.UTF_8);
    }
}
