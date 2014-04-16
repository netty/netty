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

package io.netty.handler.codec.mqtt.messages;

public final class ConnectReturnCode {

    public static final byte CONNECTION_ACCEPTED = 0x00;
    public static final byte  CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION = 0X01;
    public static final byte  CONNECTION_REFUSED_IDENTIFIER_REJECTED = 0x02;
    public static final byte  CONNECTION_REFUSED_SERVER_UNAVAILABLE = 0x03;
    public static final byte  CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD = 0x04;
    public static final byte  CONNECTION_REFUSED_NOT_AUTHORIZED = 0x05;

    private ConnectReturnCode() { }

}
