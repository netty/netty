/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.unix.tests;

import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.Socket;

import java.io.File;
import java.io.IOException;

public final class UnixTestUtils {
    public static DomainSocketAddress newSocketAddress() {
        try {
            File file;
            do {
                file = File.createTempFile("NETTY", "UDS");
                if (!file.delete()) {
                    throw new IOException("failed to delete: " + file);
                }
            } while (file.getAbsolutePath().length() > Socket.UDS_SUN_PATH_SIZE);
            return new DomainSocketAddress(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private UnixTestUtils() { }
}
