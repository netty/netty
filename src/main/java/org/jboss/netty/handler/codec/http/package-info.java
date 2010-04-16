/*
 * Copyright 2009 Red Hat, Inc.
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
 * Encoder, decoder and their related message types for HTTP.
 *
 * @apiviz.exclude ^java\.lang\.
 * @apiviz.exclude OneToOne(Encoder|Decoder)$
 * @apiviz.exclude \.HttpHeaders\.
 * @apiviz.exclude \.codec\.replay\.
 * @apiviz.exclude \.(Simple)?Channel[A-Za-z]*Handler$
 * @apiviz.exclude \.Rtsp
 * @apiviz.exclude \.Default
 * @apiviz.exclude \.Http(Client|Server)Codec$
 */
package org.jboss.netty.handler.codec.http;
