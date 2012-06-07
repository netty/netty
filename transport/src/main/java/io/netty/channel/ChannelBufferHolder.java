/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import io.netty.buffer.ChannelBuffer;

import java.util.Queue;

public final class ChannelBufferHolder<E> {

    private final Queue<E> msgBuf;
    private final ChannelBuffer byteBuf;

    ChannelBufferHolder(Queue<E> msgBuf) {
        if (msgBuf == null) {
            throw new NullPointerException("msgBuf");
        }
        this.msgBuf = msgBuf;
        byteBuf = null;

    }

    ChannelBufferHolder(ChannelBuffer byteBuf) {
        if (byteBuf == null) {
            throw new NullPointerException("byteBuf");
        }
        msgBuf = null;
        this.byteBuf = byteBuf;
    }

    public boolean hasMessageBuffer() {
        return msgBuf != null;
    }

    public boolean hasByteBuffer() {
        return byteBuf != null;
    }

    public Queue<E> messageBuffer() {
        if (msgBuf == null) {
            throw new NoSuchBufferException();
        }
        return msgBuf;
    }

    public ChannelBuffer byteBuffer() {
        if (byteBuf == null) {
            throw new NoSuchBufferException();
        }
        return byteBuf;
    }

    @Override
    public String toString() {
        if (msgBuf != null) {
            return "MessageBuffer(" + msgBuf.size() + ')';
        } else {
            return byteBuf.toString();
        }
    }

    public int size() {
        if (msgBuf != null) {
            return msgBuf.size();
        } else {
            return byteBuf.readableBytes();
        }
    }

    public boolean isEmpty() {
        if (msgBuf != null) {
            return msgBuf.isEmpty();
        } else {
            return !byteBuf.readable();
        }
    }
}
