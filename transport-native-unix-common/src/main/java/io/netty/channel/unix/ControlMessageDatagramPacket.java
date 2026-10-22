/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.unix;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.net.InetSocketAddress;

/**
 * A {@link DatagramPacket} which carries {@link ControlMessages} (also known as ancillary data, see
 * <a href="https://man7.org/linux/man-pages/man3/cmsg.3.html">cmsg(3)</a>) that are sent and received together with
 * the datagram. This is supported by the native transports (epoll, io_uring and kqueue). Other transports (like NIO)
 * handle it like any other {@link DatagramPacket}, which means the {@link ControlMessages} are ignored.
 * <p>
 * When written to a channel, the {@link ControlMessages} are sent together with the datagram.
 * <p>
 * If {@link UnixChannelOption#RECEIVE_CONTROL_MESSAGES} was used to specify the control messages that should be
 * received, each received datagram is delivered as {@link ControlMessageDatagramPacket}, which contains the
 * control messages that were received together with the datagram.
 * <p>
 * See {@link ControlMessage} for more details.
 */
public class ControlMessageDatagramPacket extends DatagramPacket {

    private final ControlMessages controlMessages;

    /**
     * Create a new instance.
     *
     * @param data              the content of the datagram.
     * @param recipient         the recipient.
     * @param controlMessages   the {@link ControlMessages}.
     */
    public ControlMessageDatagramPacket(ByteBuf data, InetSocketAddress recipient, ControlMessages controlMessages) {
        super(data, recipient);
        this.controlMessages = ObjectUtil.checkNotNull(controlMessages, "controlMessages");
    }

    /**
     * Create a new instance.
     *
     * @param data              the content of the datagram.
     * @param recipient         the recipient.
     * @param sender            the sender.
     * @param controlMessages   the {@link ControlMessages}.
     */
    public ControlMessageDatagramPacket(ByteBuf data, InetSocketAddress recipient, InetSocketAddress sender,
                                        ControlMessages controlMessages) {
        super(data, recipient, sender);
        this.controlMessages = ObjectUtil.checkNotNull(controlMessages, "controlMessages");
    }

    /**
     * Returns the {@link ControlMessages}.
     */
    public ControlMessages controlMessages() {
        return controlMessages;
    }

    /**
     * Returns the value of the given {@link ControlMessage} or {@code null} if it is not contained.
     * This is a shortcut for {@code controlMessages().get(type)}.
     */
    public <T> T controlMessage(ControlMessage<T> type) {
        return controlMessages.get(type);
    }

    @Override
    public ControlMessageDatagramPacket copy() {
        return replace(content().copy());
    }

    @Override
    public ControlMessageDatagramPacket duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public ControlMessageDatagramPacket retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public ControlMessageDatagramPacket replace(ByteBuf content) {
        return new ControlMessageDatagramPacket(content, recipient(), sender(), controlMessages);
    }

    @Override
    public ControlMessageDatagramPacket retain() {
        super.retain();
        return this;
    }

    @Override
    public ControlMessageDatagramPacket retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public ControlMessageDatagramPacket touch() {
        super.touch();
        return this;
    }

    @Override
    public ControlMessageDatagramPacket touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public String toString() {
        if (sender() != null) {
            return StringUtil.simpleClassName(this) +
                    '(' + sender() + " => " + recipient() + ", " + content() + ", " + controlMessages + ')';
        } else {
            return StringUtil.simpleClassName(this) +
                    "(=> " + recipient() + ", " + content() + ", " + controlMessages + ')';
        }
    }
}
