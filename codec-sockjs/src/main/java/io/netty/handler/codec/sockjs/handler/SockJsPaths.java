/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.handler;

import io.netty.handler.codec.sockjs.transport.TransportType;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class consists exclusively of a static method that return a {@link SockJsPath} parsing
 * a path.
 * <p>
 * A SockJS path consists of the following parts:
 * http://server:port/prefix/serverId/sessionId/transport
 */
public final class SockJsPaths {

    private static final Pattern SERVER_SESSION_PATTERN = Pattern.compile("^/([^/.]+)/([^/.]+)/([^/.]+)");
    private static final SockJsPath INVALID_PATH = new InValidSockJsPath();

    private SockJsPaths() {
    }

    /**
     * Parses the specified path and extracts SockJS path parameters.
     *
     * @param path the path to be parsed
     * @return {@link SockJsPath} the parsed SockJS path.
     */
    public static SockJsPath parse(final String path) {
        final Matcher matcher = SERVER_SESSION_PATTERN.matcher(path);
        return matcher.find() ?
                new ValidSockJsPath(matcher.group(1), matcher.group(2), matcher.group(3)) : INVALID_PATH;
    }

    /**
     * Represents HTTP path parameters in SockJS.
     *
     * The path consists of the following parts:
     * http://server:port/prefix/serverId/sessionId/transport
     *
     */
    public interface SockJsPath {

        /**
         * Returns true if the path is a valid SockJS path.
         *
         * @return @{code true} if the path isValid a SockJS path.
         */
        boolean isValid();

        /**
         * The serverId is chosen by the client and exists to make it easier to configure
         * load balancers to enable sticky sessions.
         *
         * @return String the server id for this path.
         */
        String serverId();

        /**
         * The sessionId is a unique random number which identifies the session.
         *
         * @return String the session identifier for this path.
         */
        String sessionId();

        /**
         * The type of transport.
         *
         * @return TransportType.Type the type of the transport.
         */
        TransportType transport();
    }

    private static final class ValidSockJsPath implements SockJsPath {
        private final String serverId;
        private final String sessionId;
        private final TransportType transport;

        ValidSockJsPath(final String serverId, final String sessionId, final String transport) {
            this.serverId = serverId;
            this.sessionId = sessionId;
            this.transport = TransportType.valueOf(transport.toUpperCase(Locale.ENGLISH));
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public String serverId() {
            return serverId;
        }

        @Override
        public String sessionId() {
            return sessionId;
        }

        @Override
        public TransportType transport() {
            return transport;
        }
    }

    private static final class InValidSockJsPath implements SockJsPath {

        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public String serverId() {
            throw new UnsupportedOperationException("serverId is not available in path");
        }

        @Override
        public String sessionId() {
            throw new UnsupportedOperationException("sessionId is not available in path");
        }

        @Override
        public TransportType transport() {
            throw new UnsupportedOperationException("transport is not available in path");
        }
    }

}

