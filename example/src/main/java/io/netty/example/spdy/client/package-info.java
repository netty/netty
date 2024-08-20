/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/**
 * This package contains an example SPDY HTTP client. It will behave like a SPDY-enabled browser and you can see the
 * SPDY frames flowing in and out using the {@link io.netty.example.spdy.client.SpdyFrameLogger}.
 *
 * <p>
 * After that, you can run {@link io.netty.example.spdy.client.SpdyClient}, also settings the JVM parameter
 * mentioned above.
 * <p>
 * You may also use the {@code run-example.sh} script to start the server and the client from the command line:
 * <pre>
 *     ./run-example spdy-server
 * </pre>
 * Then start the client in a different terminal window:
 * <pre>
 *     ./run-example spdy-client
 * </pre>
 */
package io.netty.example.spdy.client;
