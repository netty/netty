/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.example.http2;

import java.util.Locale;

/**
 * Utility methods used by the example client and server.
 */
public final class Http2ExampleUtil {

    /**
     * Response header sent in response to the http->http2 cleartext upgrade request.
     */
    public static final String UPGRADE_RESPONSE_HEADER = "Http-To-Http2-Upgrade";

    public static final String STANDARD_HOST = "localhost";
    public static final int STANDARD_HTTP_PORT = 8080;
    public static final int STANDARD_HTTPS_PORT = 8443;
    public static final String PORT_ARG = "-port=";
    public static final String SSL_ARG = "-ssl=";
    public static final String HOST_ARG = "-host=";

    /**
     * The configuration for the example client/server endpoints.
     */
    public static final class EndpointConfig {
        private final boolean ssl;
        private final String host;
        private final int port;

        public EndpointConfig(boolean ssl, String host, int port) {
            this.ssl = ssl;
            this.host = host;
            this.port = port;
        }

        public boolean isSsl() {
            return ssl;
        }

        public String host() {
            return host;
        }

        public int port() {
            return port;
        }

        @Override
        public String toString() {
            return "EndpointConfig [ssl=" + ssl + ", host=" + host + ", port=" + port + "]";
        }
    }

    /**
     * Parse the command-line arguments to determine the configuration for the endpoint.
     */
    public static EndpointConfig parseEndpointConfig(String[] args) {
        boolean ssl = false;
        int port = STANDARD_HTTP_PORT;
        String host = STANDARD_HOST;
        boolean portSpecified = false;
        for (String arg : args) {
            arg = arg.trim().toLowerCase(Locale.US);
            if (arg.startsWith(PORT_ARG)) {
                String value = arg.substring(PORT_ARG.length());
                port = Integer.parseInt(value);
                portSpecified = true;
            } else if (arg.startsWith(SSL_ARG)) {
                String value = arg.substring(SSL_ARG.length());
                ssl = Boolean.parseBoolean(value);
                if (!portSpecified) {
                    // Use the appropriate default.
                    port = ssl ? STANDARD_HTTPS_PORT : STANDARD_HTTP_PORT;
                }
            } else if (arg.startsWith(HOST_ARG)) {
                host = arg.substring(HOST_ARG.length());
            }
        }

        return new EndpointConfig(ssl, host, port);
    }

    private Http2ExampleUtil() {
    }
}
