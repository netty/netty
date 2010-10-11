/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
/*
Copyright (c) 2000,2001,2002,2003 ymnk, JCraft,Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

  1. Redistributions of source code must retain the above copyright notice,
     this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in
     the documentation and/or other materials provided with the distribution.

  3. The names of the authors may not be used to endorse or promote products
     derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*
 * This program is based on zlib-1.1.3, so all credit should go authors
 * Jean-loup Gailly(jloup@gzip.org) and Mark Adler(madler@alumni.caltech.edu)
 * and contributors of zlib.
 */

package org.jboss.netty.util.internal.jzlib;

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
