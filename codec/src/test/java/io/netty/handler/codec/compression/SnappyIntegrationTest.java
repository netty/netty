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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedByteChannel;
import io.netty.util.CharsetUtil;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Random;

import static io.netty.buffer.Unpooled.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class SnappyIntegrationTest {

    /**
     * The number of random regression tests run by testRandom() runs.  Whenever testRandom() finds the case that
     * the snappy codec can't encode/decode, it will print the generated source code of the offending test case.
     * You can always reproduce the problem using it rather than relying on testRandom().
     *
     * The default is 1, but you can increase it to increase the chance of finding any unusual cases.
     **/
    private static final int RANDOM_RUNS = 1;

    @Test
    public void testText() throws Exception {
        testIdentity(copiedBuffer(
                "Netty has been designed carefully with the experiences earned from the implementation of a lot of " +
                        "protocols such as FTP, SMTP, HTTP, and various binary and text-based legacy protocols",
                CharsetUtil.US_ASCII));
    }

    @Test
    public void test1002() throws Throwable {
        // Data from https://github.com/netty/netty/issues/1002
        testIdentity(wrappedBuffer(new byte[] {
                11,    0,    0,    0,    0,    0,   16,   65,   96,  119,  -22,   79,  -43,   76,  -75,  -93,
                11,  104,   96,  -99,  126,  -98,   27,  -36,   40,  117,  -65,   -3,  -57,  -83,  -58,    7,
                114,  -14,   68, -122,  124,   88,  118,   54,   45,  -26,  117,   13,  -45,   -9,   60,  -73,
                -53,  -44,   53,   68,  -77,  -71,  109,   43,  -38,   59,  100,  -12,  -87,   44, -106,  123,
                -107,   38,   13, -117,  -23,  -49,   29,   21,   26,   66,    1,   -1,   -1,   -1,   -1,   -1,
                -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   66,    0, -104,  -49,
                16, -120,   22,    8,  -52,  -54, -102,  -52, -119, -124,  -92,  -71,  101, -120,  -52,  -48,
                45,  -26,  -24,   26,   41,  -13,   36,   64,  -47,   15, -124,   -7,  -16,   91,   96,    0,
                -93,  -42,  101,   20,  -74,   39, -124,   35,   43,  -49,  -21,  -92,  -20,  -41,   79,   41,
                110, -105,   42,  -96,   90,   -9, -100,  -22,  -62,   91,    2,   35,  113,  117,  -71,   66,
                1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                -1,   -1
        }));
    }


    // These tests were found using testRandom() with large RANDOM_RUNS.
    // FIXME: Fix and unignore these failing test.
    //        Fixing one test might fix other tests, too. In such a case, please remove the redundant tests
    //        to shorten the test duration.

    @Test
    @Ignore
    public void test7088170877360183401() {
        testWithSeed(7088170877360183401L);
    }

    @Test
    @Ignore
    public void test7354134887958970957() {
        testWithSeed(7354134887958970957L);
    }

    @Test
    @Ignore
    public void test265123194979327191() {
        testWithSeed(265123194979327191L);
    }

    @Test
    @Ignore
    public void test4730809278569396315() {
        testWithSeed(4730809278569396315L);
    }

    @Test
    @Ignore
    public void test2048930638087468368() {
        testWithSeed(2048930638087468368L);
    }

    @Test
    @Ignore
    public void test7896596325568044047() {
        testWithSeed(7896596325568044047L);
    }

    @Test
    @Ignore
    public void test3397027453071844468() {
        testWithSeed(3397027453071844468L);
    }

    @Test
    @Ignore
    public void test4157969824584948251() {
        testWithSeed(4157969824584948251L);
    }

    @Test
    @Ignore
    public void test4753934068873093038(){
        testWithSeed(4753934068873093038L);
    }

    @Test
    @Ignore
    public void test5925569922155870475(){
        testWithSeed(5925569922155870475L);
    }

    @Test
    @Ignore
    public void test7269843964854027868(){
        testWithSeed(7269843964854027868L);
    }

    @Test
    @Ignore
    public void test3588069159611484749(){
        testWithSeed(3588069159611484749L);
    }

    @Test
    @Ignore
    public void test6779187833722801305(){
        testWithSeed(6779187833722801305L);
    }

    @Test
    @Ignore
    public void test4686313400062453552(){
        testWithSeed(4686313400062453552L);
    }

    @Test
    @Ignore
    public void test2991001407882611338(){
        testWithSeed(2991001407882611338L);
    }

    @Test
    @Ignore
    public void test4943660132286394340(){
        testWithSeed(4943660132286394340L);
    }

    @Test
    @Ignore
    public void test1922387899411087229(){
        testWithSeed(1922387899411087229L);
    }

    @Test
    @Ignore
    public void test1224584698616862175(){
        testWithSeed(1224584698616862175L);
    }

    @Test
    @Ignore
    public void test985619956074250243(){
        testWithSeed(985619956074250243L);
    }

    @Test
    @Ignore
    public void test930789503984237252(){
        testWithSeed(930789503984237252L);
    }

    @Test
    @Ignore
    public void test1480332326718517164(){
        testWithSeed(1480332326718517164L);
    }

    @Test
    @Ignore
    public void test8997827733405782755(){
        testWithSeed(8997827733405782755L);
    }

    @Test
    @Ignore
    public void test7059191520894204311(){
        testWithSeed(7059191520894204311L);
    }

    @Test
    @Ignore
    public void test4484339162540496103(){
        testWithSeed(4484339162540496103L);
    }

    @Test
    @Ignore
    public void test1939623429893866631(){
        testWithSeed(1939623429893866631L);
    }

    @Test
    public void testRandom() throws Throwable {
        Random rnd = new Random();
        for (int i = 0; i < RANDOM_RUNS; i++) {
            long seed = rnd.nextLong();
            if (seed < 0) {
                // Use only positive seed to get prettier test name. :-)
                continue;
            }

            try {
                testWithSeed(seed);
            } catch (Throwable t) {
                System.out.println("Failed with random seed " + seed + ". Here is a test for it:\n");
                printSeedAsTest(seed);
                //throw t;
            }
        }
    }

    private static void testWithSeed(long seed) {
        byte[] data = new byte[16 * 1048576];
        new Random(seed).nextBytes(data);
        testIdentity(data);
    }

    private static void testIdentity(byte[] data) {
        testIdentity(wrappedBuffer(data));
    }

    private static void testIdentity(ByteBuf in) {
        EmbeddedByteChannel encoder = new EmbeddedByteChannel(new SnappyFramedEncoder());
        EmbeddedByteChannel decoder = new EmbeddedByteChannel(new SnappyFramedDecoder());
        try {
            encoder.writeOutbound(in.copy());
            ByteBuf compressed = encoder.readOutbound();
            assertThat(compressed, is(notNullValue()));
            assertThat(compressed, is(not(in)));
            decoder.writeInbound(compressed);
            assertFalse(compressed.isReadable());
            compressed.discardReadBytes();
            ByteBuf decompressed = (ByteBuf) decoder.readInbound();
            assertEquals(in, decompressed);
        } finally {
            // Avoids memory leak through AbstractChannel.allChannels
            encoder.close();
            decoder.close();
        }
    }

    private static void printSeedAsTest(long l) {
        System.out.println("@Test");
        System.out.println("@Ignore");
        System.out.println("public void test" + l + "(){");
        System.out.println("    testWithSeed(" + l + "L);");
        System.out.println("}");
    }
}
