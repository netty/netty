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
import com.sun.nio.sctp.SctpChannel;
import io.netty.buffer.ChannelBuffer;
import io.netty.channel.socket.nio.SendBufferPool;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.WritableByteChannel;

final class SctpSendBufferPool extends SendBufferPool {

    private static final SctpSendBuffer EMPTY_BUFFER = new EmptySendBuffer();


    @Override
    public SctpSendBuffer acquire(Object message) {
        if (message instanceof SctpFrame) {
            return acquire((SctpFrame) message);
        } else {
            throw new IllegalArgumentException(
                    "unsupported message type: " + message.getClass() + " required: io.netty.channel.sctp.SctpFrame");
        }
    }

    private SctpSendBuffer acquire(SctpFrame message) {
        final ChannelBuffer src = message.getPayloadBuffer();
        final int streamNo = message.getStreamIdentifier();
        final int protocolId = message.getProtocolIdentifier();

        final int size = src.readableBytes();
        if (size == 0) {
            return EMPTY_BUFFER;
        }

        if (src.isDirect()) {
            return new SctpUnpooledSendBuffer(streamNo, protocolId, src.toByteBuffer());
        }
        if (src.readableBytes() > DEFAULT_PREALLOCATION_SIZE) {
            return new SctpUnpooledSendBuffer(streamNo, protocolId, src.toByteBuffer());
        }

        Preallocation current = this.current;
        ByteBuffer buffer = current.buffer;
        int remaining = buffer.remaining();
        SctpPooledSendBuffer dst;

        if (size < remaining) {
            int nextPos = buffer.position() + size;
            ByteBuffer slice = buffer.duplicate();
            buffer.position(align(nextPos));
            slice.limit(nextPos);
            current.refCnt++;
            dst = new SctpPooledSendBuffer(streamNo, protocolId, current, slice);
        } else if (size > remaining) {
            this.current = current = getPreallocation();
            buffer = current.buffer;
            ByteBuffer slice = buffer.duplicate();
            buffer.position(align(size));
            slice.limit(size);
            current.refCnt++;
            dst = new SctpPooledSendBuffer(streamNo, protocolId, current, slice);
        } else { // size == remaining
            current.refCnt++;
            this.current = getPreallocation0();
            dst = new SctpPooledSendBuffer(streamNo, protocolId, current, current.buffer);
        }

        ByteBuffer dstbuf = dst.buffer;
        dstbuf.mark();
        src.getBytes(src.readerIndex(), dstbuf);
        dstbuf.reset();
        return dst;
    }


    interface SctpSendBuffer extends SendBuffer {

        long transferTo(SctpChannel ch) throws IOException;
        
    }

    class SctpUnpooledSendBuffer extends UnpooledSendBuffer implements SctpSendBuffer {

        final int streamNo;
        final int protocolId;

        SctpUnpooledSendBuffer(int streamNo, int protocolId, ByteBuffer buffer) {
            super(buffer);
            this.streamNo = streamNo;
            this.protocolId = protocolId;
        }

        @Override
        public long transferTo(SctpChannel ch) throws IOException {
            final MessageInfo messageInfo = MessageInfo.createOutgoing(ch.association(), null, streamNo);
            messageInfo.payloadProtocolID(protocolId);
            messageInfo.streamNumber(streamNo);
            return ch.send(buffer, messageInfo);
        }
    }

    final class SctpPooledSendBuffer extends PooledSendBuffer implements SctpSendBuffer {

        final int streamNo;
        final int protocolId;

        SctpPooledSendBuffer(int streamNo, int protocolId, Preallocation parent, ByteBuffer buffer) {
            super(parent, buffer);
            this.streamNo = streamNo;
            this.protocolId = protocolId;
        }

        @Override
        public long transferTo(SctpChannel ch) throws IOException {
            final MessageInfo messageInfo = MessageInfo.createOutgoing(ch.association(), null, streamNo);
            messageInfo.payloadProtocolID(protocolId);
            messageInfo.streamNumber(streamNo);
            return ch.send(buffer, messageInfo);
        }
    }

    static final class EmptySendBuffer implements SctpSendBuffer {

        EmptySendBuffer() {
            super();
        }

        @Override
        public boolean finished() {
            return true;
        }

        @Override
        public long writtenBytes() {
            return 0;
        }

        @Override
        public long totalBytes() {
            return 0;
        }

        @Override
        public long transferTo(SctpChannel ch) throws IOException {
            return 0;
        }

        @Override
        public long transferTo(WritableByteChannel ch) throws IOException {
            return 0;
        }

        @Override
        public long transferTo(DatagramChannel ch, SocketAddress raddr) throws IOException {
            return 0;
        }
        @Override
        public void release() {
            // Unpooled.
        }
    }
}
