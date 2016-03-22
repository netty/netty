/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.mqtt;

import io.netty.handler.codec.DecoderException;

/**
 * A {@link MqttIdentifierRejectedException} which is thrown when a CONNECT request contains invalid client identifier.
 */
public final class MqttIdentifierRejectedException extends DecoderException {

    private static final long serialVersionUID = -1323503322689614981L;

    /**
     * Creates a new instance
     */
    public MqttIdentifierRejectedException() { }

    /**
     * Creates a new instance
     */
    public MqttIdentifierRejectedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance
     */
    public MqttIdentifierRejectedException(String message) {
        super(message);
    }

    /**
     * Creates a new instance
     */
    public MqttIdentifierRejectedException(Throwable cause) {
        super(cause);
    }

}
