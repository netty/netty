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

/**
 * http://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#fixed-header
 */
public class FixedHeader {

    private final int messageType;
    private final boolean isDup;
    private final int qosLevel;
    private final boolean isRetain;
    private final int remainingLength;

    public FixedHeader(int messageType, boolean isDup, int qosLevel, boolean isRetain) {
        this.messageType = messageType;
        this.isDup = isDup;
        this.qosLevel = qosLevel;
        this.isRetain = isRetain;
        this.remainingLength = 0; // this is ignored and the real value is calculated while encoding
    }

    public FixedHeader(int messageType, boolean isDup, int qosLevel, boolean isRetain, int remainingLength) {
        this.messageType = messageType;
        this.isDup = isDup;
        this.qosLevel = qosLevel;
        this.isRetain = isRetain;
        this.remainingLength = remainingLength;
    }

    public int getMessageType() {
        return messageType;
    }

    public boolean isDup() {
        return isDup;
    }

    public int getQosLevel() {
        return qosLevel;
    }

    public boolean isRetain() {
        return isRetain;
    }

    public int getRemainingLength() {
        return remainingLength;
    }
}
