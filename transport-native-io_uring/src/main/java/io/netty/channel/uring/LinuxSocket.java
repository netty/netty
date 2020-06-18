/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.unix.Socket;

public class LinuxSocket extends Socket {
    private long fd;

    public LinuxSocket(final int fd) {
        super(fd);
        this.fd = fd;
    }

    public int readEvent(long ring, long eventId, long bufferAddress, int pos, int limit) {
        return Native.read(ring, fd, eventId, bufferAddress, pos, limit);
    }

    public int writeEvent(long ring, long eventId, long bufferAddress, int pos, int limit) {
        return Native.write(ring, fd, eventId, bufferAddress, pos, limit);
    }

    public int acceptEvent(long ring, long eventId, byte[] addr) {
        return Native.accept(ring, eventId, addr);
    }

}
