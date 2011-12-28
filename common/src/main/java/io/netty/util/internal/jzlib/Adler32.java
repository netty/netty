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

/*
 * This program is based on zlib-1.1.3, so all credit should go authors
 * Jean-loup Gailly(jloup@gzip.org) and Mark Adler(madler@alumni.caltech.edu)
 * and contributors of zlib.
 */

package io.netty.util.internal.jzlib;

final class Adler32 {

    // largest prime smaller than 65536
    private static final int BASE = 65521;
    // NMAX is the largest n such that 255n(n+1)/2 + (n+1)(BASE-1) <= 2^32-1
    private static final int NMAX = 5552;

    static long adler32(long adler, byte[] buf, int index, int len) {
        if (buf == null) {
            return 1L;
        }

        long s1 = adler & 0xffff;
        long s2 = adler >> 16 & 0xffff;
        int k;

        while (len > 0) {
            k = len < NMAX? len : NMAX;
            len -= k;
            while (k >= 16) {
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                s1 += buf[index ++] & 0xff;
                s2 += s1;
                k -= 16;
            }
            if (k != 0) {
                do {
                    s1 += buf[index ++] & 0xff;
                    s2 += s1;
                } while (-- k != 0);
            }
            s1 %= BASE;
            s2 %= BASE;
        }
        return s2 << 16 | s1;
    }
}
