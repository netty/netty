/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.CharsetUtil;

/**
 * Command serialization.
 */
public class Command {
    static final byte[] ARGS_PREFIX = "*".getBytes();
    static final byte[] CRLF = "\r\n".getBytes();
    static final byte[] BYTES_PREFIX = "$".getBytes();
    static final byte[] EMPTY_BYTES = new byte[0];
    static final byte[] NEG_ONE_AND_CRLF = convertWithCRLF(-1);

    private ChannelBuffer[] arguments;
    private final Object[] objects;

    public Command(byte[]... arguments) {
        if (arguments == null) {
            this.arguments = null;
            objects = null;
        } else {
            this.arguments = new ChannelBuffer[arguments.length];
            for (int i = 0; i < arguments.length; i ++) {
                byte[] a = arguments[i];
                if (a == null) {
                    continue;
                }
                this.arguments[i] = ChannelBuffers.wrappedBuffer(a);
            }
            objects = this.arguments;
        }
    }

    public Command(ChannelBuffer[] arguments) {
        this.arguments = arguments;
        objects = arguments;
    }

    public Command(Object... objects) {
        this.objects = objects;
    }

    public String name() {
        if (arguments == null) {
            Object o = objects[0];
            if (o instanceof ChannelBuffer) {
                return ((ChannelBuffer) o).toString(CharsetUtil.UTF_8);
            }
            if (o == null) {
                return null;
            }
            return o.toString();
        }

        ChannelBuffer name = arguments[0];
        if (name == null) {
            return null;
        }
        return name.toString(CharsetUtil.UTF_8);
    }

    void write(ChannelBuffer out) {
        writeDirect(out, objects);
    }

    private static void writeDirect(ChannelBuffer out, Object... objects) {
        int length = objects.length;
        ChannelBuffer[] arguments = new ChannelBuffer[length];
        for (int i = 0; i < length; i++) {
            Object object = objects[i];
            if (object == null) {
                arguments[i] = ChannelBuffers.EMPTY_BUFFER;
            } else if (object instanceof ChannelBuffer) {
                arguments[i] = (ChannelBuffer) object;
            } else {
                arguments[i] = ChannelBuffers.copiedBuffer(object.toString(), CharsetUtil.UTF_8);
            }
        }
        writeDirect(out, arguments);
    }

    private static void writeDirect(ChannelBuffer out, ChannelBuffer[] arguments) {
        out.writeBytes(ARGS_PREFIX);
        out.writeBytes(numAndCRLF(arguments.length));
        for (ChannelBuffer argument : arguments) {
            out.writeBytes(BYTES_PREFIX);
            out.writeBytes(numAndCRLF(argument.readableBytes()));
            out.writeBytes(argument, argument.readerIndex(), argument.readableBytes());
            out.writeBytes(CRLF);
        }
    }

    private static final int NUM_MAP_LENGTH = 256;
    private static final byte[][] numAndCRLFMap = new byte[NUM_MAP_LENGTH][];
    static {
        for (int i = 0; i < NUM_MAP_LENGTH; i++) {
            numAndCRLFMap[i] = convertWithCRLF(i);
        }
    }

    // Optimized for the direct to ASCII bytes case
    // Could be even more optimized but it is already
    // about twice as fast as using Long.toString().getBytes()
    static byte[] numAndCRLF(long value) {
        if (value >= 0 && value < NUM_MAP_LENGTH) {
            return numAndCRLFMap[(int) value];
        } else if (value == -1) {
            return NEG_ONE_AND_CRLF;
        }
        return convertWithCRLF(value);
    }

    private static byte[] convertWithCRLF(long value) {
        boolean negative = value < 0;
        int index = negative ? 2 : 1;
        long current = negative ? -value : value;
        while ((current /= 10) > 0) {
            index++;
        }
        byte[] bytes = new byte[index + 2];
        if (negative) {
            bytes[0] = '-';
        }
        current = negative ? -value : value;
        long tmp = current;
        while ((tmp /= 10) > 0) {
            bytes[--index] = (byte) ('0' + current % 10);
            current = tmp;
        }
        bytes[--index] = (byte) ('0' + current);
        // add CRLF
        bytes[bytes.length - 2] = '\r';
        bytes[bytes.length - 1] = '\n';
        return bytes;
    }
}
