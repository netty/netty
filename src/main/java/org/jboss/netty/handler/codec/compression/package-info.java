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
 * Encoder and decoder which compresses and decompresses {@link org.jboss.netty.buffer.ChannelBuffer}s
 * in a compression format such as <a href="http://en.wikipedia.org/wiki/Zlib">zlib</a>
 * and <a href="http://en.wikipedia.org/wiki/Gzip">gzip</a>.
 *
 * @apiviz.exclude \.codec\.(?!compression)[a-z0-9]+\.
 * @apiviz.exclude ^java\.lang\.
 * @apiviz.exclude \.channel\.
 * @apiviz.exclude Exception$
 */
package org.jboss.netty.handler.codec.compression;
// TODO Implement bzip2 and lzma handlers
