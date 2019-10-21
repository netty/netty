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
package io.netty.channel.socket.nio;

import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.util.internal.SuppressJava6Requirement;

import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;

/**
 * Helper class which convert the {@link InternetProtocolFamily}.
 */
final class ProtocolFamilyConverter {

    private ProtocolFamilyConverter() {
        // Utility class
    }

    /**
     * Convert the {@link InternetProtocolFamily}. This MUST only be called on jdk version >= 7.
     */
    @SuppressJava6Requirement(reason = "Usage guarded by java version check")
    public static ProtocolFamily convert(InternetProtocolFamily family) {
        switch (family) {
        case IPv4:
            return StandardProtocolFamily.INET;
        case IPv6:
            return StandardProtocolFamily.INET6;
        default:
            throw new IllegalArgumentException();
        }
    }
}
