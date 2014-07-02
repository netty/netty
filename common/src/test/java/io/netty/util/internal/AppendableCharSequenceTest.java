/*
 * Copyright 2013 The Netty Project
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
package io.netty.util.internal;


import org.junit.Test;

import static org.junit.Assert.*;

public class AppendableCharSequenceTest {

    @Test
    public void testSimpleAppend() {
        testSimpleAppend0(new AppendableCharSequence(128));
    }

    @Test
    public void testAppendString() {
        testAppendString0(new AppendableCharSequence(128));
    }

    @Test
    public void testAppendAppendableCharSequence() {
        AppendableCharSequence seq = new AppendableCharSequence(128);

        String text = "testdata";
        AppendableCharSequence seq2 = new AppendableCharSequence(128);
        seq2.append(text);
        seq.append(seq2);

        assertEquals(text, seq.toString());
        assertEquals(text.substring(1, text.length() - 2), seq.substring(1, text.length() - 2));

        assertEqualsChars(text, seq);
    }

    @Test
    public void testSimpleAppendWithExpand() {
        testSimpleAppend0(new AppendableCharSequence(2));
    }

    @Test
    public void testAppendStringWithExpand() {
        testAppendString0(new AppendableCharSequence(2));
    }

    @Test
    public void testSubSequence() {
        AppendableCharSequence master = new AppendableCharSequence(26);
        master.append("abcdefghijlkmonpqrstuvwxyz");
        assertEquals("abcdefghij", master.subSequence(0, 10).toString());
    }

    private static void testSimpleAppend0(AppendableCharSequence seq) {
        String text = "testdata";
        for (int i = 0; i < text.length(); i++) {
            seq.append(text.charAt(i));
        }

        assertEquals(text, seq.toString());
        assertEquals(text.substring(1, text.length() - 2), seq.substring(1, text.length() - 2));

        assertEqualsChars(text, seq);

        seq.reset();
        assertEquals(0, seq.length());
    }

    private static void testAppendString0(AppendableCharSequence seq) {
        String text = "testdata";
        seq.append(text);

        assertEquals(text, seq.toString());
        assertEquals(text.substring(1, text.length() - 2), seq.substring(1, text.length() - 2));

        assertEqualsChars(text, seq);

        seq.reset();
        assertEquals(0, seq.length());
    }

    private static  void assertEqualsChars(CharSequence seq1, CharSequence seq2) {
        assertEquals(seq1.length(), seq2.length());
        for (int i = 0; i < seq1.length(); i++) {
            assertEquals(seq1.charAt(i), seq2.charAt(i));
        }
    }
}
