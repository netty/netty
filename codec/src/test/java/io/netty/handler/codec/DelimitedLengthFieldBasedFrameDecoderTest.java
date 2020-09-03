/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec;

import com.google.common.primitives.Bytes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.ThreadLocalRandom;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class DelimitedLengthFieldBasedFrameDecoderTest {

    public static final String TEST_DELIM = " ";
    private static final Charset CHARSET = DelimitedLengthFieldBasedFrameDecoder.CHARSET;

    private static List<List<Byte>> getRandomByteSlices(byte[] bytes) {
        List<Byte> byteList = Bytes.asList(bytes);

        int numSlices = nextInt(2, 10);
        List<Integer> sliceIndexes = new ArrayList<Integer>();
        for (int i = 0; i < numSlices; i++) {
            sliceIndexes.add(nextInt(0, bytes.length));
        }
        Collections.sort(sliceIndexes);

        List<List<Byte>> slices = new ArrayList<List<Byte>>(numSlices);

        int byteInd = 0;
        for (int sliceIndex : sliceIndexes) {
            slices.add(byteList.subList(byteInd, sliceIndex));
            byteInd = sliceIndex;
        }
        slices.add(byteList.subList(byteInd, byteList.size()));

        return slices;
    }

    /**
     * Generates a new pseudo-random integer within the specific range.
     * <p>
     * This is essentially the same method that is present in Apache commons-lang.  It is simply copied here to avoid
     * bringing in a new dependency
     *
     * @param startInclusive the lowest value that can be generated
     * @param endExclusive
     *
     * @return a pseurandom number in [startInclusive, endExclusive)
     */
    private static int nextInt(final int startInclusive, final int endExclusive) {
        if (startInclusive == endExclusive) {
            return startInclusive;
        }

        return startInclusive + ThreadLocalRandom.current().nextInt(endExclusive - startInclusive);
    }

    @Test
    public void delimitedLengths() throws Exception {
        EmbeddedChannel ch = getTestChannel(100, 0, false, true);

        String v1 = "a";
        String v2 = "abcdefghij";
        String v3 =
                "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuv" +
                "wxyz";

        writeStringAndAssert(ch, v1, false, false);
        writeStringAndAssert(ch, v2, false, false);
        writeStringAndAssert(ch, v3, false, true);

        writeStringAndAssert(ch, v1, true, false);
        writeStringAndAssert(ch, v2, true, false);
        writeStringAndAssert(ch, v3, true, true);

        writeStringAndAssert(ch, v1, false, false);

        Assert.assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void multipleFramesSingleBuffer() throws Exception {
        for (Charset charset : new Charset[] {
                CharsetUtil.ISO_8859_1, CharsetUtil.UTF_8
        }) {
            channelForMultiMessageTest(charset, charset.name().startsWith("UTF"), false);
            channelForMultiMessageTest(charset, charset.name().startsWith("UTF"), true);
        }
    }

    private void channelForMultiMessageTest(Charset charset, boolean includeUnicodeChars, boolean trimLengthString) {
        EmbeddedChannel ch = getTestChannel(100, 0, false, trimLengthString);

        List<String> frames = new LinkedList<String>(Arrays.asList("a", "xy", "xyz"));
        if (includeUnicodeChars) {
            frames.add("блин");
        }
        frames.add("cba");
        StringBuilder longFrame = new StringBuilder();
        for (String frame : frames) {
            longFrame.append(makeFrame(frame));
            if (trimLengthString) {
                longFrame.append("\n");
            }
        }

        ch.writeInbound(Unpooled.copiedBuffer(longFrame.toString(), charset));

        for (String frame : frames) {
            ByteBuf in = ch.readInbound();
            Assert.assertNotNull(in);
            Assert.assertEquals(frame, in.toString(charset));
        }

        Assert.assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void maxFrameLengthOverflow() throws Exception {
        Charset charset = CharsetUtil.ISO_8859_1;
        // maxFrameLength plus adjustment would overflow an int
        final long numBytes = Integer.MAX_VALUE - 1;
        final int lengthAdjustment = 10;
        EmbeddedChannel ch = getTestChannel((int) numBytes, lengthAdjustment, true, true);

        //this is a bad frame, but will still test the overflow condition
        String longString = String.valueOf(numBytes) + " abcd";

        try {
            ch.writeInbound(Unpooled.copiedBuffer(longString, charset));
            Assert.fail("TooLongFrameException should have been thrown");
        } catch (TooLongFrameException ignored) {
            //expected
        }
        Assert.assertNull(ch.readInbound());

        Assert.assertFalse(ch.finishAndReleaseAll());
    }

    @Test(expected = TooLongFrameException.class)
    public void delimiterTooFar() throws Exception {
        EmbeddedChannel ch = getTestChannel(Integer.MAX_VALUE, 0, true, true);
        ch.writeInbound(Unpooled.copiedBuffer("" + Long.MAX_VALUE + TEST_DELIM + "a", CHARSET));
    }

    /**
     * Tests Syslog message frames, using the Octet Counting method described in section 3.4.1 of
     * RFC 6587
     */
    @Test
    public void rfc6587SyslogMessages() {
        final List<String> syslogMessages = new LinkedList<String>();
        syslogMessages.add("<29>Apr 7 11:23:55 yourmachine su: 'su root' failed for bart on /dev/pts/5");
        syslogMessages.add("<13>Jun 3 17:32:18 10.0.2.199 sometag ls not found on path");

        syslogMessages.add("<34>1 2016-09-22T13:14:15.003Z mymachine.example.com su - \n" +
                           "ID47 - BOM'su root' failed for bart on /dev/pts/5");

        syslogMessages.add("<0>2012-08-16T09:14:29-08:00 127.0.0.1 test gcc not found");

        EmbeddedChannel ch = getTestChannel(Integer.MAX_VALUE, 0, true, false);

        for (String syslogMessage : syslogMessages) {
            writeStringAndAssert(ch, syslogMessage, true, false);
        }

        Assert.assertFalse(ch.finishAndReleaseAll());
    }

    private static EmbeddedChannel getTestChannel(int maxFrameLength, int lengthAdjustment,
                                                  boolean failFast, boolean trimLengthString) {
        return new EmbeddedChannel(
                new DelimitedLengthFieldBasedFrameDecoder(
                        maxFrameLength,
                        lengthAdjustment,
                        failFast,
                        Unpooled.copiedBuffer(TEST_DELIM, CHARSET),
                        trimLengthString
                )
        );
    }

    private static void writeStringAndAssert(EmbeddedChannel channel, String value,
                                             boolean randomlyPartition, boolean expectFrameTooLarge) {
        String frame = makeFrame(value);

        try {
            if (randomlyPartition) {
                for (List<Byte> chunk : getRandomByteSlices(frame.getBytes())) {
                    channel.writeInbound(Unpooled.copiedBuffer(Bytes.toArray(chunk)));
                }
            } else {
                channel.writeInbound(Unpooled.copiedBuffer(frame, CHARSET));
            }
        } catch (TooLongFrameException e) {
            if (!expectFrameTooLarge) {
                Assert.fail("TooLongFrameException unexpectedly thrown");
            } else {
                Assert.assertNull(channel.readInbound());
            }
        }
        if (!expectFrameTooLarge) {
            ByteBuf in = channel.readInbound();
            Assert.assertEquals(value, in.toString(CHARSET));
            in.release();
        }
    }

    private static String makeFrame(String value) {
        final byte[] bytes = value.getBytes(CHARSET);
        final int bomLength = ByteBufUtil.lengthOfByteOrderMark(Unpooled.copiedBuffer(bytes), CHARSET);
        int byteLength = bytes.length - bomLength;

        return byteLength + TEST_DELIM + value;
    }
}
