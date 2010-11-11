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
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://amitbhayani.blogspot.com/">Amit Bhayani</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2243 $, $Date: 2010-04-16 14:01:55 +0900 (Fri, 16 Apr 2010) $
 *
 * @apiviz.exclude
 */
public final class RtspVersions {

    /**
     * RTSP/1.0
     */
    public static final HttpVersion RTSP_1_0 = new HttpVersion("RTSP", 1, 0, true);

    /**
     * Returns an existing or new {@link HttpVersion} instance which matches to
     * the specified RTSP version string.  If the specified {@code text} is
     * equal to {@code "RTSP/1.0"}, {@link #RTSP_1_0} will be returned.
     * Otherwise, a new {@link HttpVersion} instance will be returned.
     */
    public static HttpVersion valueOf(String text) {
        if (text == null) {
            throw new NullPointerException("text");
        }

        text = text.trim().toUpperCase();
        if (text.equals("RTSP/1.0")) {
            return RTSP_1_0;
        }

        return new HttpVersion(text, true);
    }

    private RtspVersions() {
        super();
    }
}
