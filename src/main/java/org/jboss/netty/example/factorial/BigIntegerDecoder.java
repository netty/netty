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
package org.jboss.netty.example.factorial;

import java.math.BigInteger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

/**
 * Decodes the binary representation of a {@link BigInteger} with 32-bit
 * integer length prefix into a Java {@link BigInteger} instance.  For example,
 * { 0, 0, 0, 1, 42 } will be decoded into new BigInteger("42").
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev$, $Date$
 */
public class BigIntegerDecoder extends FrameDecoder {

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        // Wait until the length prefix is available.
        if (buffer.readableBytes() < 4) {
            return null;
        }

        int dataLength = buffer.getInt(buffer.readerIndex());

        // Wait until the whole data is available.
        if (buffer.readableBytes() < dataLength + 4) {
            return null;
        }

        // Skip the length field because we know it already.
        buffer.skipBytes(4);

        // Convert the received data into a new BigInteger.
        byte[] decoded = new byte[dataLength];
        buffer.readBytes(decoded);

        return new BigInteger(decoded);
    }
}
