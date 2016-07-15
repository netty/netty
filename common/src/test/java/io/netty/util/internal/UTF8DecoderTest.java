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

import junit.framework.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;

import com.google.common.base.Charsets;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class UTF8DecoderTest {
    private static final String THE_QUICK_BROWN_FOX = "The quick brown fox jumps over the lazy dog.";
    private static final String JAPANESE_HELLO_WORLD = "\u3053\u3093\u306b\u3061\u306f\u4e16\u754c";
    private static final String JAPANESE_WIKIPEDIA_ARTICLE = "https://ja.wikipedia.org/wiki/\u65e5\u672c";

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

    private static String japaneseWikipediaText;
    @BeforeClass
    @Ignore // Ignore because web-requests slow down stuff
    public static void fetchJapaneseWikipedia() throws IOException {
        StringBuilder builder = new StringBuilder(2 * 1024 * 1024); // Prepare for about 2MB of data
        Reader reader = new BufferedReader(new InputStreamReader(
                                new URL(JAPANESE_WIKIPEDIA_ARTICLE).openStream(),
                                Charsets.UTF_8
                            ));
        try {
            char[] buffer = new char[1024];
            int numRead;
            while ((numRead = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, numRead);
            }
        } finally {
            try {
                reader.close();
            } catch (IOException ignored) {
                // The first exception is probably more important, lets swallow this one
            }
        }
        japaneseWikipediaText = builder.toString();
    }

    @Test
    public void testDecodeJapaneseWikipedia() {
        Assume.assumeNotNull(japaneseWikipediaText); // Only run the test if we have the japanese wikipedia
        final String wikipediaText;

        byte[] encoded = japaneseWikipediaText.getBytes(Charsets.UTF_8);
        String decoded = UTF8Decoder.decode(encoded);
        Assert.assertEquals("The decoded message didn't equal the actual message.", japaneseWikipediaText, decoded);
    }
}
