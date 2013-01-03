/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.sctp;

import com.sun.nio.sctp.MessageInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

/**
 * Representation of SCTP Data Chunk
 */
public final class SctpMessage {
    private final int streamIdentifier;
    private final int protocolIdentifier;

    private final ByteBuf payloadBuffer;
    private final MessageInfo msgInfo;

    /**
     * Essential data that is being carried within SCTP Data Chunk
     * @param protocolIdentifier of payload
     * @param streamIdentifier that you want to send the payload
     * @param payloadBuffer channel buffer
     */
    public SctpMessage(int protocolIdentifier, int streamIdentifier, ByteBuf payloadBuffer) {
        this.protocolIdentifier = protocolIdentifier;
        this.streamIdentifier = streamIdentifier;
        this.payloadBuffer = payloadBuffer;
        msgInfo = null;
    }

    /**
     * Essential data that is being carried within SCTP Data Chunk
     * @param msgInfo       the {@link MessageInfo}
     * @param payloadBuffer channel buffer
     */
    public SctpMessage(MessageInfo msgInfo, ByteBuf payloadBuffer) {
        if (msgInfo == null) {
            throw new NullPointerException("msgInfo");
        }
        if (payloadBuffer == null) {
            throw new NullPointerException("payloadBuffer");
        }
        this.msgInfo = msgInfo;
        streamIdentifier = msgInfo.streamNumber();
        protocolIdentifier = msgInfo.payloadProtocolID();
        this.payloadBuffer = payloadBuffer;
    }

    /**
     * Return the stream-identifier
     */
    public int streamIdentifier() {
        return streamIdentifier;
    }

    /**
     * Return the protocol-identifier
     */
    public int protocolIdentifier() {
        return protocolIdentifier;
    }

    /**
     * Return a view of the readable bytes of the payload.
     */
    public ByteBuf payloadBuffer() {
        if (payloadBuffer.readable()) {
            return payloadBuffer.slice();
        } else {
            return Unpooled.EMPTY_BUFFER;
        }
    }

    /**
     * Return the {@link MessageInfo} for inbound messages or {@code null} for
     * outbound messages.
     */
    public MessageInfo messageInfo() {
        return msgInfo;
    }

    /**
     * Return {@code true} if this message is complete.
     */
    public boolean isComplete() {
        if (msgInfo != null) {
            return msgInfo.isComplete();
        }  else {
            //all outbound sctp messages are complete
            return true;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SctpMessage sctpFrame = (SctpMessage) o;

        if (protocolIdentifier != sctpFrame.protocolIdentifier) {
            return false;
        }

        if (streamIdentifier != sctpFrame.streamIdentifier) {
            return false;
        }

        if (!payloadBuffer.equals(sctpFrame.payloadBuffer)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = streamIdentifier;
        result = 31 * result + protocolIdentifier;
        result = 31 * result + payloadBuffer.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SctpFrame{" +
                "streamIdentifier=" + streamIdentifier + ", protocolIdentifier=" + protocolIdentifier +
                ", payloadBuffer=" + ByteBufUtil.hexDump(payloadBuffer()) + '}';
    }
}
