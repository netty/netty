/*
 * Copyright 2012 The Netty Project
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
 * <p>This package contains an example web socket web server.
 * <p>The web server only handles text, ping and closing frames.  For text frames,
 * it echoes the received text in upper case.
 * <p>Once started, you can test the web server against your browser by navigating
 * to http://localhost:8080/
 * <p>You can also test it with a web socket client. Send web socket traffic to
 * ws://localhost:8080/websocket.
 */
package io.netty.example.http.websocketx.server;
