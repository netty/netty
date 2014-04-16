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

import java.nio.charset.Charset;

/**
 * Holds Constant values used by multiple classes in mqtt-codec.
 */
public final class MqttConstants {

    static final String PROTOCOL_NAME = "MQIsdp";

    static final int QOS0 = 0;
    static final int QOS1 = 1;
    static final int QOS2 = 2;

    private MqttConstants() { }
}
