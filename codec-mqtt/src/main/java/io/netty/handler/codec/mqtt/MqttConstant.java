/*
 * Copyright 2021 The Netty Project
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

public final class MqttConstant {

    private MqttConstant() {
    }

    /**
     * Default max bytes in message
     */
    public static final int DEFAULT_MAX_BYTES_IN_MESSAGE = 8092;

    /**
     * min client id length
     */
    public static final int MIN_CLIENT_ID_LENGTH = 1;

    /**
     * Default max client id length,In the mqtt3.1 protocol,
     * the default maximum Client Identifier length is 23
     */
    public static final int DEFAULT_MAX_CLIENT_ID_LENGTH = 23;

}
