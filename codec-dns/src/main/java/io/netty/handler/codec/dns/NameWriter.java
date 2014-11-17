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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.util.AttributeKey;
import io.netty.util.internal.StringUtil;
import java.nio.charset.Charset;

/**
 * Writes names into buffers. This interface exists so that implementations can
 * be written that support DNS name compression, where names that contain
 * identical portions are addressed by reference.
 * <p>
 * The default implementation held by {@link NameWriter#DEFAULT} simply writes names out
 * in-full, in accordance with the RFC. It is also possible to implement this to
 * do name compression, per the spec. Such implementations are stateful and need
 * to be created per-request. Attach such an implementation to the {@link Channel} or
 * {@link ChannelHandlerContext} using {@link NameWriter#NAME_WRITER_KEY} and it will be retrieved by
 * {@link DnsResponseEncoder} and {@link DnsQueryEncoder} and used.
 */
public interface NameWriter {

    /**
     * Attribute key to look up a NameWriter associated with a specific
     * ChannelHandlerContext
     */
    AttributeKey<NameWriter> NAME_WRITER_KEY = AttributeKey.valueOf("nameWriter");

    /**
     * Write a name to the byte buffer in wire-format.
     *
     * @param name The name
     * @param into The buffer
     * @param encoding The encoding
     * @return this
     */
    NameWriter writeName(String name, ByteBuf into, Charset encoding);

    /**
     * The default implementation simply writes names with no compression.
     */
    NameWriter DEFAULT = new NameWriter() {

        @Override
        public NameWriter writeName(String name, ByteBuf into, Charset encoding) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            if (into == null) {
                throw new NullPointerException("buf");
            }
            if (encoding == null) {
                throw new NullPointerException("charset");
            }
            String[] parts = StringUtil.split(name, '.');
            for (String part : parts) {
                final int partLen = part.length();
                if (partLen == 0) {
                    continue;
                }
                into.writeByte(partLen);
                into.writeBytes(part.getBytes(encoding));
            }
            into.writeByte(0);
            return this;
        }
    };
}
