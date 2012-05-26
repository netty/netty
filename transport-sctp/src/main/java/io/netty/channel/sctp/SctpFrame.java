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
package io.netty.channel.sctp;

import com.sun.nio.sctp.MessageInfo;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;

/**
 * Representation of SCTP Data Chunk carried with {@link io.netty.channel.MessageEvent}.
 */
public final class SctpFrame {
    private final int streamIdentifier;
    private final int protocolIdentifier;

    private final ChannelBuffer payloadBuffer;

    private MessageInfo msgInfo;

    /**
     * Essential data that is being carried within SCTP Data Chunk
     * @param protocolIdentifier of payload
     * @param streamIdentifier that you want to send the payload
     * @param payloadBuffer channel buffer
     */
    public SctpFrame(int protocolIdentifier, int streamIdentifier, ChannelBuffer payloadBuffer) {
        this.protocolIdentifier = protocolIdentifier;
        this.streamIdentifier = streamIdentifier;
        this.payloadBuffer = payloadBuffer;
    }

    public SctpFrame(MessageInfo msgInfo, ChannelBuffer payloadBuffer) {
        this.msgInfo = msgInfo;
        this.streamIdentifier = msgInfo.streamNumber();
        this.protocolIdentifier = msgInfo.payloadProtocolID();
        this.payloadBuffer = payloadBuffer;
    }

    public int getStreamIdentifier() {
        return streamIdentifier;
    }

    public int getProtocolIdentifier() {
        return protocolIdentifier;
    }

    public ChannelBuffer getPayloadBuffer() {
        if (payloadBuffer.readable()) {
            return payloadBuffer.slice();
        } else {
            return ChannelBuffers.EMPTY_BUFFER;
        }
    }

    public MessageInfo getMessageInfo() {
        return msgInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SctpFrame sctpFrame = (SctpFrame) o;

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
        return new StringBuilder().
                append("SctpFrame{").
                append("streamIdentifier=").
                append(streamIdentifier).
                append(", protocolIdentifier=").
                append(protocolIdentifier).
                append(", payloadBuffer=").
                append(ChannelBuffers.hexDump(getPayloadBuffer())).
                append('}').toString();
    }
}
