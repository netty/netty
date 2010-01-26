/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.rtsp;

import org.jboss.netty.handler.codec.http.HttpVersion;

/**
 * The version of RTSP.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Amit Bhayani (amit.bhayani@gmail.com)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
public class RtspVersion extends HttpVersion {

    /**
     * RTSP/1.0
     */
    public static final RtspVersion RTSP_1_0 = new RtspVersion("RTSP", 1, 0);

    /**
     * Returns an existing or new {@link RtspVersion} instance which matches to
     * the specified protocol version string.  If the specified {@code text} is
     * equal to {@code "RTSP/1.0"}, {@link #RTSP_1_0} will be returned.
     * Otherwise, a new {@link RtspVersion} instance will be returned.
     */
    public static RtspVersion valueOf(String text) {
        if (text == null) {
            throw new NullPointerException("text");
        }

        text = text.trim().toUpperCase();
        if (text.equals("RTSP/1.0")) {
            return RTSP_1_0;
        }

        return new RtspVersion(text);
    }

    /**
     * Creates a new RTSP version with the specified version string.
     */
    public RtspVersion(String text) {
        super(text);
    }

    /**
     * Creates a new HTTP version with the specified protocol name and version
     * numbers.
     */
    public RtspVersion(String protocolName, int majorVersion, int minorVersion) {
        super(protocolName, majorVersion, minorVersion);
    }
}
