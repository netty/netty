/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.compression;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.Random;

public class SnappyIntegrationTest extends AbstractIntegrationTest {

    /**
     * The number of random regression tests run by testRandom() runs.  Whenever testRandom() finds the case that
     * the snappy codec can't encode/decode, it will print the generated source code of the offending test case.
     * You can always reproduce the problem using it rather than relying on testRandom().
     *
     * The default is 1, but you can increase it to increase the chance of finding any unusual cases.
     **/
    private static final int RANDOM_RUNS = 1;

    @Override
    protected EmbeddedChannel createEncoder() {
        return new EmbeddedChannel(new SnappyFrameEncoder());
    }

    @Override
    protected EmbeddedChannel createDecoder() {
        return new EmbeddedChannel(new SnappyFrameDecoder());
    }

    @Test
    public void test1002() throws Throwable {
        // Data from https://github.com/netty/netty/issues/1002
        final byte[] data = {
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
        };
        testIdentity(data);
    }

    // These tests were found using testRandom() with large RANDOM_RUNS.

    // Tests that copies do not attempt to overrun into a previous frame chunk
    @Test
    public void test5323211032315942961() {
        testWithSeed(5323211032315942961L);
    }

    // Tests that when generating the hash lookup table for finding copies, we
    // do not exceed the length of the input when there are no copies
    @Test
    public void test7088170877360183401() {
        testWithSeed(7088170877360183401L);
    }

    @Test
    public void testRandom() throws Throwable {
        for (int i = 0; i < RANDOM_RUNS; i++) {
            long seed = rand.nextLong();
            if (seed < 0) {
                // Use only positive seed to get prettier test name. :-)
                continue;
            }

            try {
                testWithSeed(seed);
            } catch (Throwable t) {
                System.out.println("Failed with random seed " + seed + ". Here is a test for it:\n");
                printSeedAsTest(seed);
                throw t;
            }
        }
    }

    private void testWithSeed(long seed) {
        byte[] data = new byte[16 * 1048576];
        new Random(seed).nextBytes(data);
        testIdentity(data);
    }

    private static void printSeedAsTest(long l) {
        System.out.println("@Test");
        System.out.println("@Ignore");
        System.out.println("public void test" + l + "(){");
        System.out.println("    testWithSeed(" + l + "L);");
        System.out.println("}");
    }
}
