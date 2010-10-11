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
 * Extensible decoder and its common implementations which deal with the
 * packet fragmentation and reassembly issue found in a stream-based transport
 * such as TCP/IP.
 *
 * @apiviz.exclude OneToOne(Encoder|Decoder)$
 * @apiviz.exclude \.(Simple)?Channel[A-Za-z]*Handler$
 * @apiviz.exclude \.codec\.[a-eg-z][a-z0-9]*\.
 * @apiviz.exclude \.ssl\.
 */
package org.jboss.netty.handler.codec.frame;
