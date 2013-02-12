/*
 * Copyright 2013 The Netty Project
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
package io.netty.verify.all;

import static org.junit.Assert.*;

import java.net.URL;

import org.junit.Test;

/**
 * Netty-All-in-One Integration Tests.
 */
public class NettyMarkerIT {

    public final static String[] MARKER_ARRAY = new String[] {
    /** netty module */
    "META-INF/netty-buffer.marker",
    /** netty module */
    "META-INF/netty-codec.marker",
    /** netty module */
    "META-INF/netty-codec-http.marker",
    /** netty module */
    "META-INF/netty-codec-socks.marker",
    /** netty module */
    "META-INF/netty-common.marker",
    /** netty module */
    "META-INF/netty-handler.marker",
    /** netty module */
    "META-INF/netty-transport.marker",
    /** netty module */
    "META-INF/netty-transport-rxtx.marker",
    /** netty module */
    "META-INF/netty-transport-sctp.marker",
    /** netty module */
    "META-INF/netty-transport-udt.marker" };

    public void verifyMarkerPresent(final String path) throws Exception {
        final URL url = getClass().getClassLoader().getResource(path);
        assertNotNull("missing marker=" + path, url);
    }

    @Test
    public void verifyMarkersPresent() throws Exception {
        for (final String marker : MARKER_ARRAY) {
            verifyMarkerPresent(marker);
        }
    }

}
