/*
 * Copyright 2023 The Netty Project
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

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UnixDatagramPacket extends DatagramPacket {

    private final List<ControlMessage> controlMessages;

    public UnixDatagramPacket(ByteBuf data, InetSocketAddress recipient) {
        super(data, recipient);
        this.controlMessages = Collections.emptyList();
    }

    public UnixDatagramPacket(ByteBuf data, InetSocketAddress recipient, InetSocketAddress sender) {
        super(data, recipient, sender);
        this.controlMessages = Collections.emptyList();
    }

    public UnixDatagramPacket(ByteBuf data, InetSocketAddress recipient,
                              ControlMessage... controlMessages) {
        super(data, recipient);
        this.controlMessages = buildControlMessages(controlMessages);
    }

    public UnixDatagramPacket(ByteBuf data, InetSocketAddress recipient, InetSocketAddress sender,
                              ControlMessage... controlMessages) {
        super(data, recipient, sender);
        this.controlMessages = buildControlMessages(controlMessages);
    }

    private static List<ControlMessage> buildControlMessages(ControlMessage... controlMessages) {
        if (controlMessages == null || controlMessages.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(controlMessages);
    }

    public List<ControlMessage> controlMessages() {
        return controlMessages;
    }
}
