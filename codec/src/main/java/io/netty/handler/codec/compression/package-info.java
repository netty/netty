/*
 * Copyright 2012 The Netty Project
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
 * <p>Encoder and decoder which compresses and decompresses {@link io.netty.buffer.ByteBuf}s
 * in a compression format such as</p>
 *
 *      <a href="http://en.wikipedia.org/wiki/Zlib">zlib</a><br>
 *      <a href="http://en.wikipedia.org/wiki/Gzip">gzip</a><br>
 *      <a href="http://code.google.com/p/snappy/">Snappy</a><br>
 *      <a href="http://en.wikipedia.org/wiki/Bzip2">bzip2</a><br>
 *      <a href="https://github.com/ning/compress/wiki">LZF</a><br>
 *      <a href="http://en.wikipedia.org/wiki/LZ4_%28compression_algorithm%29">LZ4</a><br>
 *      <a href="http://fastlz.org/">FastLZ</a><br>
 *      <a href="http://en.wikipedia.org/wiki/Lempel%E2%80%93Ziv%E2%80%93Markov_chain_algorithm">LZMA</a><br>
 */
package io.netty.handler.codec.compression;
