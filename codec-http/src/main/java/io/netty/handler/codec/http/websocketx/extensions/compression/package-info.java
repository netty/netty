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
 * Encoder, decoder, handshakers to handle most common WebSocket Compression Extensions.
 * <p>
 * This package supports different web socket extensions.
 * The specification currently supported are:
 * <ul>
 * <li><a href="https://tools.ietf.org/html/draft-ietf-hybi-permessage-compression-18">permessage-deflate</a></li>
 * <li><a href="https://tools.ietf.org/id/draft-tyoshino-hybi-websocket-perframe-deflate-06.txt">
 * perframe-deflate and x-webkit-deflate-frame</a></li>
 * </ul>
 * </p>
 * <p>
 * See <tt>io.netty.example.http.websocketx.client.WebSocketClient</tt> and
 * <tt>io.netty.example.http.websocketx.html5.WebSocketServer</tt> for usage.
 * </p>
 */
package io.netty.handler.codec.http.websocketx.extensions.compression;
