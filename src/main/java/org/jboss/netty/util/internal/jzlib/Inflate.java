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

import org.jboss.netty.util.internal.jzlib.JZlib.WrapperType;

final class Inflate {

    private static final int METHOD = 0; // waiting for method byte
    private static final int FLAG = 1; // waiting for flag byte
    private static final int DICT4 = 2; // four dictionary check bytes to go
    private static final int DICT3 = 3; // three dictionary check bytes to go
    private static final int DICT2 = 4; // two dictionary check bytes to go
    private static final int DICT1 = 5; // one dictionary check byte to go
    private static final int DICT0 = 6; // waiting for inflateSetDictionary
    private static final int BLOCKS = 7; // decompressing blocks
    private static final int CHECK4 = 8; // four check bytes to go
    private static final int CHECK3 = 9; // three check bytes to go
    private static final int CHECK2 = 10; // two check bytes to go
    private static final int CHECK1 = 11; // one check byte to go
    private static final int DONE = 12; // finished check, done
    private static final int BAD = 13; // got an error--stay here

    private static final int GZIP_ID1 = 14;
    private static final int GZIP_ID2 = 15;
    private static final int GZIP_CM = 16;
    private static final int GZIP_FLG = 17;
    private static final int GZIP_MTIME_XFL_OS = 18;
    private static final int GZIP_XLEN = 19;
    private static final int GZIP_FEXTRA = 20;
    private static final int GZIP_FNAME = 21;
    private static final int GZIP_FCOMMENT = 22;
    private static final int GZIP_FHCRC = 23;
    private static final int GZIP_CRC32= 24;
    private static final int GZIP_ISIZE = 25;

    private int mode; // current inflate mode
    // mode dependent information
    private int method; // if FLAGS, method byte
    // if CHECK, check values to compare
    private final long[] was = new long[1]; // computed check value
    private long need; // stream check value
    // if BAD, inflateSync's marker bytes count
    private int marker;
    // mode independent information
    private WrapperType wrapperType;
    private int wbits; // log2(window size)  (8..15, defaults to 15)
    private InfBlocks blocks; // current inflate_blocks state
    private int gzipFlag;
    private int gzipBytesToRead;
    private int gzipXLen;
    private int gzipUncompressedBytes;
    private int gzipCRC32;
    private int gzipISize;

    private int inflateReset(ZStream z) {
        if (z == null || z.istate == null) {
            return JZlib.Z_STREAM_ERROR;
        }

        z.total_in = z.total_out = 0;
        z.msg = null;
        switch (wrapperType) {
        case NONE:
            z.istate.mode = BLOCKS;
            break;
        case ZLIB:
            z.istate.mode = METHOD;
            break;
        case GZIP:
            z.istate.mode = GZIP_ID1;
            break;
        }
        z.istate.blocks.reset(z, null);
        gzipUncompressedBytes = 0;
        return JZlib.Z_OK;
    }

    int inflateEnd(ZStream z) {
        if (blocks != null) {
            blocks.free(z);
        }
        blocks = null;
        //    ZFREE(z, z->state);
        return JZlib.Z_OK;
    }

    int inflateInit(ZStream z, int w, WrapperType wrapperType) {
        z.msg = null;
        blocks = null;

        this.wrapperType = wrapperType;

        if (w < 0) {
            throw new IllegalArgumentException("w: " + w);
        }

        // set window size
        if (w < 8 || w > 15) {
            inflateEnd(z);
            return JZlib.Z_STREAM_ERROR;
        }
        wbits = w;

        z.istate.blocks = new InfBlocks(
                z, z.istate.wrapperType == WrapperType.NONE? null : this,
                1 << w);

        // reset state
        inflateReset(z);
        return JZlib.Z_OK;
    }

    int inflate(ZStream z, int f) {
        int r;
        int b;

        if (z == null || z.istate == null || z.next_in == null) {
            return JZlib.Z_STREAM_ERROR;
        }
        f = f == JZlib.Z_FINISH? JZlib.Z_BUF_ERROR : JZlib.Z_OK;
        r = JZlib.Z_BUF_ERROR;
        while (true) {
            //System.out.println("mode: "+z.istate.mode);
            switch (z.istate.mode) {
            case METHOD:

                if (z.avail_in == 0) {
                    return r;
                }
                r = f;

                z.avail_in --;
                z.total_in ++;
                if (((z.istate.method = z.next_in[z.next_in_index ++]) & 0xf) != JZlib.Z_DEFLATED) {
                    z.istate.mode = BAD;
                    z.msg = "unknown compression method";
                    z.istate.marker = 5; // can't try inflateSync
                    break;
                }
                if ((z.istate.method >> 4) + 8 > z.istate.wbits) {
                    z.istate.mode = BAD;
                    z.msg = "invalid window size";
                    z.istate.marker = 5; // can't try inflateSync
                    break;
                }
                z.istate.mode = FLAG;
            case FLAG:

                if (z.avail_in == 0) {
                    return r;
                }
                r = f;

                z.avail_in --;
                z.total_in ++;
                b = z.next_in[z.next_in_index ++] & 0xff;

                if (((z.istate.method << 8) + b) % 31 != 0) {
                    z.istate.mode = BAD;
                    z.msg = "incorrect header check";
                    z.istate.marker = 5; // can't try inflateSync
                    break;
                }

                if ((b & JZlib.PRESET_DICT) == 0) {
                    z.istate.mode = BLOCKS;
                    break;
                }
                z.istate.mode = DICT4;
            case DICT4:

                if (z.avail_in == 0) {
                    return r;
                }
                r = f;

                z.avail_in --;
                z.total_in ++;
                z.istate.need = (z.next_in[z.next_in_index ++] & 0xff) << 24 & 0xff000000L;
                z.istate.mode = DICT3;
            case DICT3:

                if (z.avail_in == 0) {
                    return r;
                }
                r = f;

                z.avail_in --;
                z.total_in ++;
                z.istate.need += (z.next_in[z.next_in_index ++] & 0xff) << 16 & 0xff0000L;
                z.istate.mode = DICT2;
            case DICT2:

                if (z.avail_in == 0) {
                    return r;
                }
                r = f;

                z.avail_in --;
                z.total_in ++;
                z.istate.need += (z.next_in[z.next_in_index ++] & 0xff) << 8 & 0xff00L;
                z.istate.mode = DICT1;
            case DICT1:

                if (z.avail_in == 0) {
                    return r;
                }

                z.avail_in --;
                z.total_in ++;
                z.istate.need += z.next_in[z.next_in_index ++] & 0xffL;
                z.adler = z.istate.need;
                z.istate.mode = DICT0;
                return JZlib.Z_NEED_DICT;
            case DICT0:
                z.istate.mode = BAD;
                z.msg = "need dictionary";
                z.istate.marker = 0; // can try inflateSync
                return JZlib.Z_STREAM_ERROR;
            case BLOCKS:
                int old_next_out_index = z.next_out_index;
                try {
                    r = z.istate.blocks.proc(z, r);
                    if (r == JZlib.Z_DATA_ERROR) {
                        z.istate.mode = BAD;
                        z.istate.marker = 0; // can try inflateSync
                        break;
                    }
                    if (r == JZlib.Z_OK) {
                        r = f;
                    }
                    if (r != JZlib.Z_STREAM_END) {
                        return r;
                    }
                    r = f;
                    z.istate.blocks.reset(z, z.istate.was);
                } finally {
                    int decompressedBytes = z.next_out_index - old_next_out_index;
                    gzipUncompressedBytes += decompressedBytes;
                    z.crc32 = CRC32.crc32(z.crc32, z.next_out, old_next_out_index, decompressedBytes);
                }
                if (z.istate.wrapperType == WrapperType.NONE) {
                    z.istate.mode = DONE;
                    break;
                } else if (z.istate.wrapperType == WrapperType.ZLIB) {
                    z.istate.mode = CHECK4;
                } else {
                    gzipCRC32 = 0;
                    gzipISize = 0;
                    gzipBytesToRead = 4;
                    z.istate.mode = GZIP_CRC32;
                    break;
                }
            case CHECK4:
                if (z.avail_in == 0) {
                    return r;
                }
                r = f;

                z.avail_in --;
                z.total_in ++;
                z.istate.need = (z.next_in[z.next_in_index ++] & 0xff) << 24 & 0xff000000L;
                z.istate.mode = CHECK3;
            case CHECK3:
                if (z.avail_in == 0) {
                    return r;
                }
                r = f;

                z.avail_in --;
                z.total_in ++;
                z.istate.need += (z.next_in[z.next_in_index ++] & 0xff) << 16 & 0xff0000L;
                z.istate.mode = CHECK2;
            case CHECK2:
                if (z.avail_in == 0) {
                    return r;
                }
                r = f;

                z.avail_in --;
                z.total_in ++;
                z.istate.need += (z.next_in[z.next_in_index ++] & 0xff) << 8 & 0xff00L;
                z.istate.mode = CHECK1;
            case CHECK1:
                if (z.avail_in == 0) {
                    return r;
                }
                r = f;

                z.avail_in --;
                z.total_in ++;
                z.istate.need += z.next_in[z.next_in_index ++] & 0xffL;

                if ((int) z.istate.was[0] != (int) z.istate.need) {
                    z.istate.mode = BAD;
                    z.msg = "incorrect data check";
                    z.istate.marker = 5; // can't try inflateSync
                    break;
                }

                z.istate.mode = DONE;
            case DONE:
                return JZlib.Z_STREAM_END;
            case BAD:
                return JZlib.Z_DATA_ERROR;
            case GZIP_ID1:
                if (z.avail_in == 0) {
                    return r;
                }
                r = f;
                z.avail_in --;
                z.total_in ++;

                if ((z.next_in[z.next_in_index ++] & 0xff) != 31) {
                    z.istate.mode = BAD;
                    z.msg = "not a gzip stream";
                    z.istate.marker = 5; // can't try inflateSync
                    break;
                }
                z.istate.mode = GZIP_ID2;
            case GZIP_ID2:
                if (z.avail_in == 0) {
                    return r;
                }
                r = f;
                z.avail_in --;
                z.total_in ++;

                if ((z.next_in[z.next_in_index ++] & 0xff) != 139) {
                    z.istate.mode = BAD;
                    z.msg = "not a gzip stream";
                    z.istate.marker = 5; // can't try inflateSync
                    break;
                }
                z.istate.mode = GZIP_CM;
            case GZIP_CM:
                if (z.avail_in == 0) {
                    return r;
                }
                r = f;
                z.avail_in --;
                z.total_in ++;

                if ((z.next_in[z.next_in_index ++] & 0xff) != JZlib.Z_DEFLATED) {
                    z.istate.mode = BAD;
                    z.msg = "unknown compression method";
                    z.istate.marker = 5; // can't try inflateSync
                    break;
                }
                z.istate.mode = GZIP_FLG;
            case GZIP_FLG:
                if (z.avail_in == 0) {
                    return r;
                }
                r = f;
                z.avail_in --;
                z.total_in ++;
                gzipFlag = z.next_in[z.next_in_index ++] & 0xff;

                if ((gzipFlag & 0xE2) != 0) {
                    z.istate.mode = BAD;
                    z.msg = "unsupported flag";
                    z.istate.marker = 5; // can't try inflateSync
                    break;
                }
                gzipBytesToRead = 6;
                z.istate.mode = GZIP_MTIME_XFL_OS;
            case GZIP_MTIME_XFL_OS:
                while (gzipBytesToRead > 0) {
                    if (z.avail_in == 0) {
                        return r;
                    }
                    r = f;
                    z.avail_in --;
                    z.total_in ++;
                    z.next_in_index ++;
                    gzipBytesToRead --;
                }
                z.istate.mode = GZIP_XLEN;
                gzipXLen = 0;
                gzipBytesToRead = 2;
            case GZIP_XLEN:
                if ((gzipFlag & 4) != 0) {
                    while (gzipBytesToRead > 0) {
                        if (z.avail_in == 0) {
                            return r;
                        }
                        r = f;
                        z.avail_in --;
                        z.total_in ++;
                        gzipXLen |= (z.next_in[z.next_in_index ++] & 0xff) << (1 - gzipBytesToRead) * 8;
                        gzipBytesToRead --;
                    }
                    gzipBytesToRead = gzipXLen;
                    z.istate.mode = GZIP_FEXTRA;
                } else {
                    z.istate.mode = GZIP_FNAME;
                    break;
                }
            case GZIP_FEXTRA:
                while (gzipBytesToRead > 0) {
                    if (z.avail_in == 0) {
                        return r;
                    }
                    r = f;
                    z.avail_in --;
                    z.total_in ++;
                    z.next_in_index ++;
                    gzipBytesToRead --;
                }
                z.istate.mode = GZIP_FNAME;
            case GZIP_FNAME:
                if ((gzipFlag & 8) != 0) {
                    do {
                        if (z.avail_in == 0) {
                            return r;
                        }
                        r = f;
                        z.avail_in --;
                        z.total_in ++;
                    } while (z.next_in[z.next_in_index ++] != 0);
                }
                z.istate.mode = GZIP_FCOMMENT;
            case GZIP_FCOMMENT:
                if ((gzipFlag & 16) != 0) {
                    do {
                        if (z.avail_in == 0) {
                            return r;
                        }
                        r = f;
                        z.avail_in --;
                        z.total_in ++;
                    } while (z.next_in[z.next_in_index ++] != 0);
                }
                gzipBytesToRead = 2;
                z.istate.mode = GZIP_FHCRC;
            case GZIP_FHCRC:
                if ((gzipFlag & 2) != 0) {
                    while (gzipBytesToRead > 0) {
                        if (z.avail_in == 0) {
                            return r;
                        }
                        r = f;
                        z.avail_in --;
                        z.total_in ++;
                        z.next_in_index ++;
                        gzipBytesToRead --;
                    }
                }
                z.istate.mode = BLOCKS;
                break;
            case GZIP_CRC32:
                while (gzipBytesToRead > 0) {
                    if (z.avail_in == 0) {
                        return r;
                    }
                    r = f;
                    z.avail_in --;
                    z.total_in ++;
                    gzipBytesToRead --;
                    z.istate.gzipCRC32 |= (z.next_in[z.next_in_index ++] & 0xff) << (3 - gzipBytesToRead) * 8;
                }

                if (z.crc32 != z.istate.gzipCRC32) {
                    z.istate.mode = BAD;
                    z.msg = "incorrect CRC32 checksum";
                    z.istate.marker = 5; // can't try inflateSync
                    break;
                }
                gzipBytesToRead = 4;
                z.istate.mode = GZIP_ISIZE;
            case GZIP_ISIZE:
                while (gzipBytesToRead > 0) {
                    if (z.avail_in == 0) {
                        return r;
                    }
                    r = f;
                    z.avail_in --;
                    z.total_in ++;
                    gzipBytesToRead --;
                    z.istate.gzipISize |= (z.next_in[z.next_in_index ++] & 0xff) << (3 - gzipBytesToRead) * 8;
                }

                if (gzipUncompressedBytes != z.istate.gzipISize) {
                    z.istate.mode = BAD;
                    z.msg = "incorrect ISIZE checksum";
                    z.istate.marker = 5; // can't try inflateSync
                    break;
                }

                z.istate.mode = DONE;
                break;
            default:
                return JZlib.Z_STREAM_ERROR;
            }
        }
    }

    int inflateSetDictionary(ZStream z, byte[] dictionary, int dictLength) {
        int index = 0;
        int length = dictLength;
        if (z == null || z.istate == null || z.istate.mode != DICT0) {
            return JZlib.Z_STREAM_ERROR;
        }

        if (Adler32.adler32(1L, dictionary, 0, dictLength) != z.adler) {
            return JZlib.Z_DATA_ERROR;
        }

        z.adler = Adler32.adler32(0, null, 0, 0);

        if (length >= 1 << z.istate.wbits) {
            length = (1 << z.istate.wbits) - 1;
            index = dictLength - length;
        }
        z.istate.blocks.set_dictionary(dictionary, index, length);
        z.istate.mode = BLOCKS;
        return JZlib.Z_OK;
    }

    private static final byte[] mark = { (byte) 0, (byte) 0, (byte) 0xff, (byte) 0xff };

    int inflateSync(ZStream z) {
        int n; // number of bytes to look at
        int p; // pointer to bytes
        int m; // number of marker bytes found in a row
        long r, w; // temporaries to save total_in and total_out

        // set up
        if (z == null || z.istate == null) {
            return JZlib.Z_STREAM_ERROR;
        }
        if (z.istate.mode != BAD) {
            z.istate.mode = BAD;
            z.istate.marker = 0;
        }
        if ((n = z.avail_in) == 0) {
            return JZlib.Z_BUF_ERROR;
        }
        p = z.next_in_index;
        m = z.istate.marker;

        // search
        while (n != 0 && m < 4) {
            if (z.next_in[p] == mark[m]) {
                m ++;
            } else if (z.next_in[p] != 0) {
                m = 0;
            } else {
                m = 4 - m;
            }
            p ++;
            n --;
        }

        // restore
        z.total_in += p - z.next_in_index;
        z.next_in_index = p;
        z.avail_in = n;
        z.istate.marker = m;

        // return no joy or set up to restart on a new block
        if (m != 4) {
            return JZlib.Z_DATA_ERROR;
        }
        r = z.total_in;
        w = z.total_out;
        inflateReset(z);
        z.total_in = r;
        z.total_out = w;
        z.istate.mode = BLOCKS;
        return JZlib.Z_OK;
    }
}
