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

import org.jboss.netty.handler.codec.http.HttpHeaders;

/**
 * Standard RTSP header names and values.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Amit Bhayani (amit.bhayani@gmail.com)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
public class RtspHeaders {

    /**
     * Standard RTSP header names.
     *
     * @author The Netty Project (netty-dev@lists.jboss.org)
     * @author Amit Bhayani (amit.bhayani@gmail.com)
     * @author Trustin Lee (trustin@gmail.com)
     * @version $Rev$, $Date$
     */
    public static class Names extends HttpHeaders.Names {
        /**
         * {@code "Allow"}
         */
        public static final String ALLOW = "Allow";
        /**
         * {@code "Bandwidth"}
         */
        public static final String BANDWIDTH = "Bandwidth";
        /**
         * {@code "Blocksize"}
         */
        public static final String BLOCKSIZE = "Blocksize";
        /**
         * {@code "Conference"}
         */
        public static final String CONFERENCE = "Conference";
        /**
         * {@code "CSeq"}
         */
        public static final String CSEQ = "CSeq";
        /**
         * {@code "Proxy-Require"}
         */
        public static final String PROXY_REQUIRE = "Proxy-Require";
        /**
         * {@code "Public"}
         */
        public static final String PUBLIC = "Public";
        /**
         * {@code "Require"}
         */
        public static final String REQUIRE = "Require";
        /**
         * {@code "RTP-Info"}
         */
        public static final String RTP_INFO = "RTP-Info";
        /**
         * {@code "Scale"}
         */
        public static final String SCALE = "Scale";
        /**
         * {@code "Speed"}
         */
        public static final String SPEED = "Speed";
        /**
         * {@code "Session"}
         */
        public static final String SESSION = "Session";
        /**
         * {@code "Timestamp"}
         */
        public static final String TIMESTAMP = "Timestamp";
        /**
         * {@code "Transport"}
         */
        public static final String TRANSPORT = "Transport";
        /**
         * {@code "Unsupported"}
         */
        public static final String UNSUPPORTED = "Unsupported";

        protected Names() {
            super();
        }
    }

    /**
     * Standard RTSP header names.
     *
     * @author The Netty Project (netty-dev@lists.jboss.org)
     * @author Trustin Lee (trustin@gmail.com)
     * @version $Rev$, $Date$
     */
    public static class Values extends HttpHeaders.Values {
        /**
         * {@code "append"}
         */
        public static final String APPEND = "append";
        /**
         * {@code "AVP"}
         */
        public static final String AVP = "AVP";
        /**
         * {@code "client_port"}
         */
        public static final String CLIENT_PORT = "client_port";
        /**
         * {@code "destination"}
         */
        public static final String DESTINATION = "destination";
        /**
         * {@code "interleaved"}
         */
        public static final String INTERLEAVED = "interleaved";
        /**
         * {@code "layers"}
         */
        public static final String LAYERS = "layers";
        /**
         * {@code "mode"}
         */
        public static final String MODE = "mode";
        /**
         * {@code "multicast"}
         */
        public static final String MULTICAST = "multicast";
        /**
         * {@code "port"}
         */
        public static final String PORT = "port";
        /**
         * {@code "RTP"}
         */
        public static final String RTP = "RTP";
        /**
         * {@code "rtptime"}
         */
        public static final String RTPTIME = "rtptime";
        /**
         * {@code "seq"}
         */
        public static final String SEQ = "seq";
        /**
         * {@code "server_port"}
         */
        public static final String SERVER_PORT = "server_port";
        /**
         * {@code "ssrc"}
         */
        public static final String SSRC = "ssrc";
        /**
         * {@code "TCP"}
         */
        public static final String TCP = "TCP";
        /**
         * {@code "timeout"}
         */
        public static final String TIMEOUT = "timeout";
        /**
         * {@code "ttl"}
         */
        public static final String TTL = "ttl";
        /**
         * {@code "UDP"}
         */
        public static final String UDP = "UDP";
        /**
         * {@code "unicast"}
         */
        public static final String UNICAST = "unicast";
        /**
         * {@code "url"}
         */
        public static final String URL = "url";

        protected Values() {
            super();
        }
    }

    protected RtspHeaders() {
        super();
    }
}
