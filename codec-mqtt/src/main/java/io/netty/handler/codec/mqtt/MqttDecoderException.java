/*
 * Copyright 2026 The Netty Project
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

import io.netty.handler.codec.DecoderException;
import io.netty.util.internal.ObjectUtil;

/**
 * A decoder failure that exposes the corresponding MQTT 5 DISCONNECT reason code.
 *
 * <p>The decoder does not send a DISCONNECT packet. Applications can inspect
 * {@link #reasonCode()} to decide how to handle the failed message. This
 * exception remains a {@link DecoderException} for compatibility with callers
 * that already handle decoder failures.
 */
public final class MqttDecoderException extends DecoderException {

    private static final long serialVersionUID = 1L;

    private final MqttReasonCodes.Disconnect reasonCode;

    /**
     * Creates a decoder exception with an MQTT 5 reason code.
     *
     * @param reasonCode the corresponding DISCONNECT reason code
     * @param message the diagnostic message
     */
    public MqttDecoderException(MqttReasonCodes.Disconnect reasonCode, String message) {
        super(message);
        this.reasonCode = ObjectUtil.checkNotNull(reasonCode, "reasonCode");
    }

    /**
     * Creates a decoder exception with a cause and an MQTT 5 reason code.
     *
     * @param reasonCode the corresponding DISCONNECT reason code
     * @param message the diagnostic message
     * @param cause the original decoding failure
     */
    public MqttDecoderException(MqttReasonCodes.Disconnect reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = ObjectUtil.checkNotNull(reasonCode, "reasonCode");
    }

    /**
     * Returns the MQTT 5 reason code corresponding to this decoding failure.
     *
     * @return the DISCONNECT reason code
     */
    public MqttReasonCodes.Disconnect reasonCode() {
        return reasonCode;
    }
}
