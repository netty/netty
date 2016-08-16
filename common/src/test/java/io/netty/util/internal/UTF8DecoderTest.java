/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.util.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Random;

import com.google.common.base.Charsets;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.io.Closeables;
import com.google.common.net.HttpHeaders;
import com.google.common.primitives.Bytes;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ComparisonFailure;
import org.junit.Ignore;
import org.junit.Test;

public class UTF8DecoderTest {
    private static final String THE_QUICK_BROWN_FOX = "The quick brown fox jumps over the lazy dog.";
    private static final String JAPANESE_HELLO_WORLD = "\u3053\u3093\u306b\u3061\u306f\u4e16\u754c";
    private static final String JAPANESE_WIKIPEDIA_ARTICLE = "https://ja.wikipedia.org/wiki/\u65e5\u672c";
    private static final String SURROGATES = "Look at dem surrogates: äÄ∏ŒŒ";

    @Test
    public void testDecodeSurrogates() {
        byte[] encoded = SURROGATES.getBytes(Charsets.UTF_8);
        String decoded = UTF8Decoder.decode(encoded);
        Assert.assertEquals("The decoded message didn't equal the actual message.", SURROGATES, decoded);
    }

    @Test
    public void testDecodeEnglish() {
        byte[] encoded = THE_QUICK_BROWN_FOX.getBytes(Charsets.UTF_8);
        String decoded = UTF8Decoder.decode(encoded);
        Assert.assertEquals("The decoded message didn't equal the actual message.", THE_QUICK_BROWN_FOX, decoded);
    }

    @Test
    public void testDecodeJapanese() {
        byte[] encoded = JAPANESE_HELLO_WORLD.getBytes(Charsets.UTF_8);
        String decoded = UTF8Decoder.decode(encoded);
        Assert.assertEquals("The decoded message didn't equal the actual message.", JAPANESE_HELLO_WORLD, decoded);
    }

    private static String japaneseWikipediaText, githubText;
    @BeforeClass
    @Ignore
    public static void fetchWebpages() throws IOException {
        japaneseWikipediaText = fetchWebpage(JAPANESE_WIKIPEDIA_ARTICLE);
        githubText = fetchWebpage("https://github.com/");
    }

    private static String fetchWebpage(String url) throws IOException {
        URLConnection connection = null;
        Reader reader = null;
        try {
            connection = new URL(JAPANESE_WIKIPEDIA_ARTICLE).openConnection();
            StringBuilder builder = new StringBuilder(
                    (int) connection.getHeaderFieldLong(
                            HttpHeaders.CONTENT_LENGTH, // Try the content-length
                            8 * 1024 * 1024 // Default to 8MB if Content-Length isn't specified
                    )
            );
            reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(),
                    Charsets.UTF_8
            ));
            char[] buffer = new char[1024];
            int numRead;
            while ((numRead = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, numRead);
            }
            return builder.toString();
        } finally {
            Closeables.closeQuietly(reader);
        }
    }

    @Test
    public void testDecodeGithub() {
        Assume.assumeNotNull(githubText); // Only run the test if we have the japanese wikipedia
        byte[] encoded = githubText.getBytes(Charsets.UTF_8);
        String decoded = UTF8Decoder.decode(encoded);
        Assert.assertEquals("The decoded message didn't equal the actual message.", githubText, decoded);
    }

    @Test
    public void testDecodeJapaneseWikipedia() {
        Assume.assumeNotNull(japaneseWikipediaText); // Only run the test if we have the japanese wikipedia
        byte[] encoded = japaneseWikipediaText.getBytes(Charsets.UTF_8);
        String decoded = UTF8Decoder.decode(encoded);
        Assert.assertEquals("The decoded message didn't equal the actual message.", japaneseWikipediaText, decoded);
    }

    private static final byte[] CONTINUATION_BYTES = Bytes.toArray(ContiguousSet.create(Range.closed(0x80, 0xBF), DiscreteDomain.integers()));
    /**
     * Bytes that are illegal anywhere in the sequence
     */
    private static final byte[] ILLEGAL_BYTES = new byte[]{
            (byte) 0xC0,
            (byte) 0xC1,
            (byte) 0xF5,
            (byte) 0xF6,
            (byte) 0xF7,
            (byte) 0xF8,
            (byte) 0xF9,
            (byte) 0xFA,
            (byte) 0xFB,
            (byte) 0xFC,
            (byte) 0xFD,
            (byte) 0xFE,
            (byte) 0xFF,
    };
    /**
     * Encodings that take too much space to represent its data
     */
    private static final byte[][] OVERLONG_ENCODINGS = new byte[][]{
            {
                    (byte) 0xF0,
                    (byte) 0x82,
                    (byte) 0x82,
                    (byte) 0xAC
            },
            {
                    (byte) 0xE0,
                    (byte) 0x80,
                    (byte) 0x80
            },
            {
                    (byte) 0xE0,
                    (byte) 0x80,
                    (byte) 0xC0,
            },
            {
                    (byte) 0xE0,
                    (byte) 0x9a,
                    (byte) 0x1c,
            },
            {
                    (byte) 0xF8,
                    (byte) 0x80,
                    (byte) 0x80,
                    (byte) 0x81,
                    (byte) 0xBF
            },
            {
                    (byte) 0xF0,
                    (byte) 0xB5
            }
    };
    /**
     * Encodings that exceed the maximum unicode code point
     */
    private static final byte[][] OVERFLOW_ENCODINGS = new byte[][]{
            {
                    (byte) 0xF4,
                    (byte) 0x90,
                    (byte) 0x80,
                    (byte) 0xC0
            },
            {
                    (byte) 0xF4,
                    (byte) 0x90,
                    (byte) 0x80,
                    (byte) 0xC0
            },
            {
                    (byte) 0xF5,
                    (byte) 0x80,
                    (byte) 0x80,
                    (byte) 0xC0
            }
    };
    private static final byte[][] SURROGATE_CODE_POINTS = new byte[][]{
            {
                    (byte) 0xED,
                    (byte) 0xAF,
                    (byte) 0x80
            }
    };
    private static final byte[][] INSUFFICIENT_CONTINUATIONS = new byte[][]{
            {
                    (byte) 0xE2,
                    (byte) 0x82
            },
            {
                    (byte) 0xF0,
                    (byte) 0x90,
                    (byte) 0x8D
            },
            {
                    (byte) 0xCF,
                    (byte) 0x39,
                    (byte) 0x0D
            },
            {
                    // Insufficient continuation followed by invalid byte
                    (byte) 0xE6,
                    (byte) 0x81,
                    (byte) 0xFB
            }
    };
    //
    // Test proper handling of invalid data required of UTF8 implementations
    //

    private static byte[] bytesOfRange(int start, int end) {
        if ((byte) start != start) {
            throw new IllegalArgumentException("Start " + Integer.toHexString(start) + " can't fit in a byte");
        } else if ((byte) end != end) {
            throw new IllegalArgumentException("End " + Integer.toHexString(end) + " can't fit in a byte");
        } else if (start > end) {
            throw new IllegalArgumentException("Start " + Integer.toHexString(start) + " is greater than end " + Integer.toHexString(end));
        }
        int size = end - start;
        byte[] result = new byte[size];
        for (int i = 0; i < size; i++) {
            result[i] = (byte) (start + i);
        }
        return result;
    }

    private static void testDecoding(byte[] encoded) {
        String expectedResult = new String(encoded, Charsets.UTF_8);
        String result;
        try {
            result = UTF8Decoder.decode(encoded);
        } catch (Throwable t) {
            AssertionError error = new AssertionError(
                    "Caught throwable while decoding sequence "
                            + StringUtil.toHexString(encoded)
                            + ". The JDK got "
                            + expectedResult
            );
            //noinspection UnnecessaryInitCause - the cause argument was only added in 1.7
            error.initCause(t);
            throw error;
        }
        if (!result.equals(expectedResult)) {
            UTF8Decoder.decode(encoded);
            throw new ComparisonFailure(
                    "Unexpected result of decoding  sequence "
                            + StringUtil.toHexString(encoded),
                    expectedResult,
                    result
            );
        }
    }


    @Test
    public void testIllegalBytes() {
        for (byte b : ILLEGAL_BYTES) {
            testDecoding(new byte[]{b});
        }
    }

    @Test
    public void testOverlongEncodings() {
        for (byte[] encoded : OVERLONG_ENCODINGS) {
            testDecoding(encoded);
        }
    }

    @Test
    public void testOverflowEncodings() {
        for (byte[] encoded : OVERFLOW_ENCODINGS) {
            testDecoding(encoded);
        }
    }

    @Test
    public void testSurrogateCodePoints() {
        for (byte[] surrogateCodePoint : SURROGATE_CODE_POINTS) {
            testDecoding(surrogateCodePoint);
        }
    }

    @Test
    public void testInsufficientContinuations() {
        for (byte[] encoded : INSUFFICIENT_CONTINUATIONS) {
            testDecoding(encoded);
        }
    }

    @Test
    @Ignore
    public void testRandomData() throws IOException {
        Random random = new Random();
        byte[] bytes = new byte[10]; // 10 bytes
        for (int i = 0; i < 1000; i++) {
            random.nextBytes(bytes);
            testDecoding(bytes);
        }
    }
}
