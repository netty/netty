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
package io.netty.example.spdyclient;

import static io.netty.handler.codec.spdy.SpdyOrHttpChooser.SelectedProtocol.HTTP_1_1;
import static io.netty.handler.codec.spdy.SpdyOrHttpChooser.SelectedProtocol.SPDY_3_1;

import java.util.List;

import org.eclipse.jetty.npn.NextProtoNego.ClientProvider;

/**
 * The Jetty project provides an implementation of the Transport Layer Security (TLS) extension for Next
 * Protocol Negotiation (NPN) for OpenJDK 7 or greater. NPN allows the application layer to negotiate which
 * protocol to use over the secure connection.
 * <p>
 * This NPN service provider negotiates using SPDY.
 * <p>
 * To enable NPN support, start the JVM with: {@code java -Xbootclasspath/p:<path_to_npn_boot_jar> ...}. The
 * "path_to_npn_boot_jar" is the path on the file system for the NPN Boot Jar file which can be downloaded from
 * Maven at coordinates org.mortbay.jetty.npn:npn-boot. Different versions applies to different OpenJDK versions.
 *
 * @see http://www.eclipse.org/jetty/documentation/current/npn-chapter.html
 */
public class SpdyClientProvider implements ClientProvider {

    private String selectedProtocol;

    @Override
    public String selectProtocol(List<String> protocols) {

        if (protocols.contains(SPDY_3_1.protocolName())) {
            return SPDY_3_1.protocolName();
        }

        return selectedProtocol;
    }

    @Override
    public boolean supports() {
        return true;
    }

    @Override
    public void unsupported() {
        this.selectedProtocol = HTTP_1_1.protocolName();
    }

}
