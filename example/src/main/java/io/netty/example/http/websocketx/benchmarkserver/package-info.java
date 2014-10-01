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

/**
 * <p>This package contains a benchmark application for websockets.
 * <p>The websocket server will accept websocket upgrade requests and simply echo
 * all incoming frames.
 * <p>The supplied index page contains a benchmark client that runs in a browser.
 * <p>Once started, you can start benchmarking by by navigating
 * to http://localhost:8080/ with your browser.
 * <p>You can also test it with a web socket client. Send web socket traffic to
 * ws://localhost:8080/websocket.
 */
package io.netty.example.http.websocketx.benchmarkserver;
