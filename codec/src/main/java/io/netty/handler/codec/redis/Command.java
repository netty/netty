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
package io.netty.handler.codec.redis;

import io.netty.buffer.ChannelBuffer;
import io.netty.util.CharsetUtil;

import java.io.IOException;

/**
 * Command serialization.
 */
public class Command {
    public static final byte[] ARGS_PREFIX = "*".getBytes();
    public static final byte[] CRLF = "\r\n".getBytes();
    public static final byte[] BYTES_PREFIX = "$".getBytes();
    public static final byte[] EMPTY_BYTES = new byte[0];
    public static final byte[] NEG_ONE_AND_CRLF = convertWithCRLF(-1);

    private byte[][] arguments;
    private Object[] objects;

    public String getName() {
        if (arguments == null) {
            Object o = objects[0];
            if (o instanceof byte[]) {
                return new String((byte[]) o);
            } else {
                return o.toString();
            }
        } else {
            return new String(arguments[0]);
        }
    }

    public Command(byte[]... arguments) {
        this.arguments = arguments;
        objects = arguments;
    }

    public Command(Object... objects) {
        this.objects = objects;
    }

    public void write(ChannelBuffer os) throws IOException {
        writeDirect(os, objects);
    }

    public static void writeDirect(ChannelBuffer os, Object... objects) throws IOException {
        int length = objects.length;
        byte[][] arguments = new byte[length][];
        for (int i = 0; i < length; i++) {
            Object object = objects[i];
            if (object == null) {
                arguments[i] = EMPTY_BYTES;
            } else if (object instanceof byte[]) {
                arguments[i] = (byte[]) object;
            } else {
                arguments[i] = object.toString().getBytes(CharsetUtil.UTF_8);
            }
        }
        writeDirect(os, arguments);
    }

    private static void writeDirect(ChannelBuffer os, byte[][] arguments) throws IOException {
        os.writeBytes(ARGS_PREFIX);
        os.writeBytes(numAndCRLF(arguments.length));
        for (byte[] argument : arguments) {
            os.writeBytes(BYTES_PREFIX);
            os.writeBytes(numAndCRLF(argument.length));
            os.writeBytes(argument);
            os.writeBytes(CRLF);
        }
    }

    private static final int NUM_MAP_LENGTH = 256;
    private static byte[][] numAndCRLFMap = new byte[NUM_MAP_LENGTH][];
    static {
      for (int i = 0; i < NUM_MAP_LENGTH; i++) {
        numAndCRLFMap[i] = convertWithCRLF(i);
      }
    }

    // Optimized for the direct to ASCII bytes case
    // Could be even more optimized but it is already
    // about twice as fast as using Long.toString().getBytes()
    public static byte[] numAndCRLF(long value) {
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
        bytes[--index] = (byte) ('0' + (current % 10));
        current = tmp;
      }
      bytes[--index] = (byte) ('0' + current);
      // add CRLF
      bytes[bytes.length - 2] = '\r';
      bytes[bytes.length - 1] = '\n';
      return bytes;
    }

}
