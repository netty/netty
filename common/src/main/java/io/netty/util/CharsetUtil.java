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
package io.netty.util;

import io.netty.util.internal.InternalThreadLocalMap;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.Map;

/**
 * A utility class that provides various common operations and constants
 * related with {@link Charset} and its relevant classes.
 */
public final class CharsetUtil {

    /**
     * 16-bit UTF (UCS Transformation Format) whose byte order is identified by
     * an optional byte-order mark
     */
    public static final Charset UTF_16 = Charset.forName("UTF-16");

    /**
     * 16-bit UTF (UCS Transformation Format) whose byte order is big-endian
     */
    public static final Charset UTF_16BE = Charset.forName("UTF-16BE");

    /**
     * 16-bit UTF (UCS Transformation Format) whose byte order is little-endian
     */
    public static final Charset UTF_16LE = Charset.forName("UTF-16LE");

    /**
     * 8-bit UTF (UCS Transformation Format)
     */
    public static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * ISO Latin Alphabet No. 1, as known as <tt>ISO-LATIN-1</tt>
     */
    public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

    /**
     * 7-bit ASCII, as known as ISO646-US or the Basic Latin block of the
     * Unicode character set
     */
    public static final Charset US_ASCII = Charset.forName("US-ASCII");

    private static final Charset[] CHARSETS = new Charset[]
            { UTF_16, UTF_16BE, UTF_16LE, UTF_8, ISO_8859_1, US_ASCII };

    public static Charset[] values() { return CHARSETS; }

    /**
     * @deprecated Use {@link #encoder(Charset)}.
     */
    @Deprecated
    public static CharsetEncoder getEncoder(Charset charset) {
        return encoder(charset);
    }

    /**
     * Returns a new {@link CharsetEncoder} for the {@link Charset} with specified error actions.
     *
     * @param charset The specified charset
     * @param malformedInputAction The encoder's action for malformed-input errors
     * @param unmappableCharacterAction The encoder's action for unmappable-character errors
     * @return The encoder for the specified {@code charset}
     */
    public static CharsetEncoder encoder(Charset charset, CodingErrorAction malformedInputAction,
                                         CodingErrorAction unmappableCharacterAction) {
        checkNotNull(charset, "charset");
        CharsetEncoder e = charset.newEncoder();
        e.onMalformedInput(malformedInputAction).onUnmappableCharacter(unmappableCharacterAction);
        return e;
    }

    /**
     * Returns a new {@link CharsetEncoder} for the {@link Charset} with the specified error action.
     *
     * @param charset The specified charset
     * @param codingErrorAction The encoder's action for malformed-input and unmappable-character errors
     * @return The encoder for the specified {@code charset}
     */
    public static CharsetEncoder encoder(Charset charset, CodingErrorAction codingErrorAction) {
        return encoder(charset, codingErrorAction, codingErrorAction);
    }

    /**
     * Returns a cached thread-local {@link CharsetEncoder} for the specified {@link Charset}.
     *
     * @param charset The specified charset
     * @return The encoder for the specified {@code charset}
     */
    public static CharsetEncoder encoder(Charset charset) {
        checkNotNull(charset, "charset");

        Map<Charset, CharsetEncoder> map = InternalThreadLocalMap.get().charsetEncoderCache();
        CharsetEncoder e = map.get(charset);
        if (e != null) {
            e.reset().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
            return e;
        }

        e = encoder(charset, CodingErrorAction.REPLACE, CodingErrorAction.REPLACE);
        map.put(charset, e);
        return e;
    }

    /**
     * @deprecated Use {@link #decoder(Charset)}.
     */
    @Deprecated
    public static CharsetDecoder getDecoder(Charset charset) {
        return decoder(charset);
    }

    /**
     * Returns a new {@link CharsetDecoder} for the {@link Charset} with specified error actions.
     *
     * @param charset The specified charset
     * @param malformedInputAction The decoder's action for malformed-input errors
     * @param unmappableCharacterAction The decoder's action for unmappable-character errors
     * @return The decoder for the specified {@code charset}
     */
    public static CharsetDecoder decoder(Charset charset, CodingErrorAction malformedInputAction,
                                         CodingErrorAction unmappableCharacterAction) {
        checkNotNull(charset, "charset");
        CharsetDecoder d = charset.newDecoder();
        d.onMalformedInput(malformedInputAction).onUnmappableCharacter(unmappableCharacterAction);
        return d;
    }

    /**
     * Returns a new {@link CharsetDecoder} for the {@link Charset} with the specified error action.
     *
     * @param charset The specified charset
     * @param codingErrorAction The decoder's action for malformed-input and unmappable-character errors
     * @return The decoder for the specified {@code charset}
     */
    public static CharsetDecoder decoder(Charset charset, CodingErrorAction codingErrorAction) {
        return decoder(charset, codingErrorAction, codingErrorAction);
    }

    /**
     * Returns a cached thread-local {@link CharsetDecoder} for the specified {@link Charset}.
     *
     * @param charset The specified charset
     * @return The decoder for the specified {@code charset}
     */
    public static CharsetDecoder decoder(Charset charset) {
        checkNotNull(charset, "charset");

        Map<Charset, CharsetDecoder> map = InternalThreadLocalMap.get().charsetDecoderCache();
        CharsetDecoder d = map.get(charset);
        if (d != null) {
            d.reset().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
            return d;
        }

        d = decoder(charset, CodingErrorAction.REPLACE, CodingErrorAction.REPLACE);
        map.put(charset, d);
        return d;
    }

    private CharsetUtil() { }
}
