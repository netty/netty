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
package org.jboss.netty.channel.socket.nio;

import java.net.ProtocolFamily;


/**
 * Helper class which convert the {@link ProtocolFamily}. 
 * 
 *
 */
final class ProtocolFamilyConverter {

    private ProtocolFamilyConverter() {
        // Utility class
    }
    
    /**
     * Convert the {@link NioDatagramChannel.ProtocolFamily}. This MUST only be called on jdk version >= 7.
     */
    public static ProtocolFamily convert(NioDatagramChannel.ProtocolFamily family) {
        switch (family) {
        case INET:
            return java.net.StandardProtocolFamily.INET;

        case INET6:
            return java.net.StandardProtocolFamily.INET6;
        default:
            throw new IllegalArgumentException();
        }
    }
}
