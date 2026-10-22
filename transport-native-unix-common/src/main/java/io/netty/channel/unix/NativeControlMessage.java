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

import io.netty.channel.ChannelException;
import io.netty.channel.unix.ControlMessageConstants.Descriptor;

import java.io.IOException;

/**
 * A {@link ControlMessage} that is supported by Netty out of the box. All the native constants that are needed to
 * encode / decode it are resolved lazily as these can only be loaded once the native library was loaded.
 *
 * @param <T> the type of the value of the control message.
 */
abstract class NativeControlMessage<T> extends ControlMessage<T> {

    // Lazily resolved as the native library might not be loaded yet when the constants are created.
    // This is racy but fine as Descriptor is immutable.
    private Descriptor descriptor;

    NativeControlMessage(String name) {
        super(name);
    }

    final Descriptor descriptor() {
        Descriptor descriptor = this.descriptor;
        if (descriptor == null) {
            descriptor = ControlMessageConstants.descriptor(name());
            this.descriptor = descriptor;
        }
        return descriptor;
    }

    @Override
    final boolean isSendSupported() {
        return descriptor().sendType != -1;
    }

    @Override
    final int sendLevel() {
        return descriptor().level;
    }

    @Override
    final int sendType() {
        return descriptor().sendType;
    }

    @Override
    final boolean isReceiveSupported() {
        return descriptor().receiveType != -1;
    }

    @Override
    final boolean isReceived(int level, int type) {
        Descriptor descriptor = descriptor();
        return descriptor.receiveType != -1 && descriptor.level == level && descriptor.receiveType == type;
    }

    @Override
    final void setReceiveEnabled(Socket socket, boolean enabled) throws IOException {
        Descriptor descriptor = descriptor();
        if (descriptor.receiveOption == -1) {
            throw new UnsupportedOperationException("Receiving " + name() + " is not supported");
        }
        try {
            socket.setIntOpt(descriptor.level, descriptor.receiveOption, enabled ? 1 : 0);
        } catch (ChannelException e) {
            // setsockopt(...) failures are reported via a ChannelException.
            if (socket.ipv6 && descriptor.level == ControlMessageConstants.IPPROTO_IP) {
                // Not every platform supports IPv4 level socket options on IPv6 sockets (for example macOS and
                // the BSDs). These platforms use the IPv6 control messages for IPv4-mapped IPv6 addresses, so
                // ignore the failure.
                return;
            }
            if (!socket.ipv6 && descriptor.level == ControlMessageConstants.IPPROTO_IPV6) {
                // An IPv4 socket will never receive any IPv6 datagrams, so it is fine if we can't enable these.
                return;
            }
            throw e;
        }
    }
}
