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

public final class MessageType {

    public static final int CONNECT = 1;
    public static final int CONNACK = 2;
    public static final int PUBLISH = 3;
    public static final int PUBACK = 4;
    public static final int PUBREC = 5;
    public static final int PUBREL = 6;
    public static final int PUBCOMP = 7;
    public static final int SUBSCRIBE = 8;
    public static final int SUBACK = 9;
    public static final int UNSUBSCRIBE = 10;
    public static final int UNSUBACK = 11;
    public static final int PINGREQ = 12;
    public static final int PINGRESP = 13;
    public static final int DISCONNECT = 14;

    private MessageType() { }

}
