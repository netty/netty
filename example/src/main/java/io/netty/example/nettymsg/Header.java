/*
 *
 *  * Copyright 2020 The Netty Project
 *  *
 *  * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 *  * "License"); you may not use this file except in compliance with the License. You may obtain a
 *  * copy of the License at:
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License
 *  * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  * or implied. See the License for the specific language governing permissions and limitations under
 *  * the License.
 *
 */

package io.netty.example.nettymsg;

import java.util.HashMap;
import java.util.Map;

/**
 * custom message header
 */
public class Header {
    private int crcCode = 0xabef0101;
    private int length;
    private long sessionId;
    //MessageType
    private byte type;
    private byte priority;
    private Map<String, Object> attachment = new HashMap<String, Object>();

    public Header(byte type) {
        this.type = type;
    }

    public Header() {
    }

    //message type
    public enum MessageType {
        BIZ_REQ((byte) 0),
        BIZ_RESP((byte) 1),
        ONE_WAY_REQ((byte) 2),
        HANDSHAKE_REQ((byte) 3),
        HANDSHAKE_RESP((byte) 4),
        HEARTBEAT_REQ((byte) 5),
        HEARTBEAT_RESP((byte) 6),
        ;
        byte value;

        MessageType(byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }
    }

    public int getCrcCode() {
        return crcCode;
    }

    public void setCrcCode(int crcCode) {
        this.crcCode = crcCode;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public byte getType() {
        return type;
    }

    public void setType(byte type) {
        this.type = type;
    }

    public byte getPriority() {
        return priority;
    }

    public void setPriority(byte priority) {
        this.priority = priority;
    }

    public Map<String, Object> getAttachment() {
        return attachment;
    }

    public void setAttachment(Map<String, Object> attachment) {
        this.attachment = attachment;
    }
}
