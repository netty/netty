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
package org.jboss.netty.channel.socket.sctp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://github.com/jestan">Jestan Nirojan</a>
 */
public final class SctpMessage {
    private final int streamNo;
    private final int payloadProtocolId;
    private final ChannelBuffer data;

    public SctpMessage(int streamNo, int payloadProtocolId, ChannelBuffer data) {
        this.streamNo = streamNo;
        this.payloadProtocolId = payloadProtocolId;
        this.data = data;
    }

    public int streamNumber() {
        return streamNo;
    }

    public int payloadProtocolId() {
        return payloadProtocolId;
    }

    public ChannelBuffer data() {
        if (data.readable()) {
            return data.slice();
        } else {
            return ChannelBuffers.EMPTY_BUFFER;
        }
    }

    @Override
    public String toString() {
        return new StringBuilder().
                append("SctpMessage{").
                append("streamNo=").
                append(streamNo).
                append(", payloadProtocolId=").
                append(payloadProtocolId).
                append(", data=").
                append(ChannelBuffers.hexDump(data())).
                append('}').toString();
    }
}
