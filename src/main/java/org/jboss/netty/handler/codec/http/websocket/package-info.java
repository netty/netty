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

/**
 * Encoder, decoder and their related message types for
 * <a href="http://en.wikipedia.org/wiki/Web_Sockets">Web Socket</a> data frames.
 * <p>
 * For the detailed instruction on adding add Web Socket support to your HTTP
 * server, take a look into the <tt>WebSocketServer</tt> example located in the
 * {@code org.jboss.netty.example.http.websocket} package.
 * *
 * @apiviz.exclude OneToOne(Encoder|Decoder)$
 * @apiviz.exclude \.codec\.replay\.
 * @apiviz.exclude \.Default
 */
package org.jboss.netty.handler.codec.http.websocket;
