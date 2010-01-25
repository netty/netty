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
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Amit Bhayani (amit.bhayani@gmail.com)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
public class RtspHeaders {

    public static class Names extends HttpHeaders.Names {

        public static final String ALLOW = "Allow";

        public static final String BANDWIDTH = "Bandwidth";

        public static final String BLOCKSIZE = "Blocksize";

        public static final String CONFERENCE = "Conference";

        public static final String CSEQ = "CSeq";

        public static final String PROXY_REQUIRE = "Proxy-Require";

        public static final String PUBLIC = "Public";

        public static final String REQUIRE = "Require";

        public static final String RTP_INFO = "RTP-Info";

        public static final String SCALE = "Scale";

        public static final String SPEED = "Speed";

        public static final String SESSION = "Session";

        public static final String TIMESTAMP = "Timestamp";

        public static final String TRANSPORT = "Transport";

        public static final String UNSUPPORTED = "Unsupported";

        protected Names() {
            super();
        }
    }

    public static class Values extends HttpHeaders.Values {

        public static final String APPEND = "append";
        public static final String AVP = "AVP";
        public static final String CLIENT_PORT = "client_port";
        public static final String DESTINATION = "destination";
        public static final String INTERLEAVED = "interleaved";
        public static final String LAYERS = "layers";
        public static final String MODE = "mode";
        public static final String MULTICAST = "multicast";
        public static final String PORT = "port";
        public static final String RTP = "RTP";
        public static final String RTPTIME = "rtptime";
        public static final String SEQ = "seq";
        public static final String SERVER_PORT = "server_port";
        public static final String SSRC = "ssrc";
        public static final String TCP = "TCP";
        public static final String TIMEOUT = "timeout";
        public static final String TTL = "ttl";
        public static final String UDP = "UDP";
        public static final String UNICAST = "unicast";
        public static final String URL = "url";

        protected Values() {
            super();
        }
    }
}
