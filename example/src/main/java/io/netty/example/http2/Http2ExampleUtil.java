/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.example.http2;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Utility methods used by the example client and server.
 */
public final class Http2ExampleUtil {

    /**
     * Response header sent in response to the http-&gt;http2 cleartext upgrade request.
     */
    public static final String UPGRADE_RESPONSE_HEADER = "http-to-http2-upgrade";

    /**
     * Size of the block to be read from the input stream.
     */
    private static final int BLOCK_SIZE = 1024;

    private Http2ExampleUtil() { }

    /**
     * @param string the string to be converted to an integer.
     * @param defaultValue the default value
     * @return the integer value of a string or the default value, if the string is either null or empty.
     */
    public static int toInt(String string, int defaultValue) {
        if (string != null && !string.isEmpty()) {
            return Integer.parseInt(string);
        }
        return defaultValue;
    }

    /**
     * Reads an InputStream into a byte array.
     * @param input the InputStream.
     * @return a byte array representation of the InputStream.
     * @throws IOException if an I/O exception of some sort happens while reading the InputStream.
     */
    public static ByteBuf toByteBuf(InputStream input) throws IOException {
        ByteBuf buf = Unpooled.buffer();
        int n = 0;
        do {
            n = buf.writeBytes(input, BLOCK_SIZE);
        } while (n > 0);
        return buf;
    }

    /**
     * @param query the decoder of query string
     * @param key the key to lookup
     * @return the first occurrence of that key in the string parameters
     */
    public static String firstValue(QueryStringDecoder query, String key) {
        checkNotNull(query, "Query can't be null!");
        List<String> values = query.parameters().get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}
