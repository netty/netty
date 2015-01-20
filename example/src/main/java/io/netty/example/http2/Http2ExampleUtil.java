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

/**
 * Utility methods used by the example client and server.
 */
public final class Http2ExampleUtil {

    /**
     * Response header sent in response to the http->http2 cleartext upgrade request.
     */
    public static final String UPGRADE_RESPONSE_HEADER = "Http-To-Http2-Upgrade";

    private Http2ExampleUtil() { }
}
