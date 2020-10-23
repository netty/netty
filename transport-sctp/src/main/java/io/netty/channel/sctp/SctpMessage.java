/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.sctp;

import com.sun.nio.sctp.MessageInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.util.internal.ObjectUtil;

/**
 * Representation of SCTP Data Chunk
 */
public final class SctpMessage extends DefaultByteBufHolder {
    private final int streamIdentifier;
    private final int protocolIdentifier;
    private final boolean unordered;

    private final MessageInfo msgInfo;

    /**
     * Essential data that is being carried within SCTP Data Chunk
     * @param protocolIdentifier of payload
     * @param streamIdentifier that you want to send the payload
     * @param payloadBuffer channel buffer
     */
    public SctpMessage(int protocolIdentifier, int streamIdentifier, ByteBuf payloadBuffer) {
        this(protocolIdentifier, streamIdentifier, false, payloadBuffer);
    }

    /**
     * Essential data that is being carried within SCTP Data Chunk
     * @param protocolIdentifier of payload
     * @param streamIdentifier that you want to send the payload
     * @param unordered if {@literal true}, the SCTP Data Chunk will be sent with the U (unordered) flag set.
     * @param payloadBuffer channel buffer
     */
    public SctpMessage(int protocolIdentifier, int streamIdentifier, boolean unordered, ByteBuf payloadBuffer) {
        super(payloadBuffer);
        this.protocolIdentifier = protocolIdentifier;
        this.streamIdentifier = streamIdentifier;
        this.unordered = unordered;
        msgInfo = null;
    }

    /**
     * Essential data that is being carried within SCTP Data Chunk
     * @param msgInfo       the {@link MessageInfo}
     * @param payloadBuffer channel buffer
     */
    public SctpMessage(MessageInfo msgInfo, ByteBuf payloadBuffer) {
        super(payloadBuffer);
        this.msgInfo = ObjectUtil.checkNotNull(msgInfo, "msgInfo");
        this.streamIdentifier = msgInfo.streamNumber();
        this.protocolIdentifier = msgInfo.payloadProtocolID();
        this.unordered = msgInfo.isUnordered();
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
     * return the unordered flag
     */
    public boolean isUnordered() {
        return unordered;
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

        if (unordered != sctpFrame.unordered) {
            return false;
        }

        return content().equals(sctpFrame.content());
    }

    @Override
    public int hashCode() {
        int result = streamIdentifier;
        result = 31 * result + protocolIdentifier;
        // values 1231 and 1237 are referenced in the javadocs of Boolean#hashCode()
        result = 31 * result + (unordered ? 1231 : 1237);
        result = 31 * result + content().hashCode();
        return result;
    }

    @Override
    public SctpMessage copy() {
        return (SctpMessage) super.copy();
    }

    @Override
    public SctpMessage duplicate() {
        return (SctpMessage) super.duplicate();
    }

    @Override
    public SctpMessage retainedDuplicate() {
        return (SctpMessage) super.retainedDuplicate();
    }

    @Override
    public SctpMessage replace(ByteBuf content) {
        if (msgInfo == null) {
            return new SctpMessage(protocolIdentifier, streamIdentifier, unordered, content);
        } else {
            return new SctpMessage(msgInfo, content);
        }
    }

    @Override
    public SctpMessage retain() {
        super.retain();
        return this;
    }

    @Override
    public SctpMessage retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public SctpMessage touch() {
        super.touch();
        return this;
    }

    @Override
    public SctpMessage touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public String toString() {
        return "SctpFrame{" +
               "streamIdentifier=" + streamIdentifier + ", protocolIdentifier=" + protocolIdentifier +
               ", unordered=" + unordered +
               ", data=" + contentToString() + '}';
    }
}
