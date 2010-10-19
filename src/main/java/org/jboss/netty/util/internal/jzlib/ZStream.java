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

public final class ZStream {

    public byte[] next_in; // next input byte
    public int next_in_index;
    public int avail_in; // number of bytes available at next_in
    public long total_in; // total nb of input bytes read so far
    public byte[] next_out; // next output byte should be put there
    public int next_out_index;
    public int avail_out; // remaining free space at next_out
    public long total_out; // total nb of bytes output so far
    public String msg;
    Deflate dstate;
    Inflate istate;
    long adler;
    int crc32;

    public int inflateInit() {
        return inflateInit(JZlib.DEF_WBITS);
    }

    public int inflateInit(Enum<?> wrapperType) {
        return inflateInit(JZlib.DEF_WBITS, wrapperType);
    }

    public int inflateInit(int w) {
        return inflateInit(w, WrapperType.ZLIB);
    }

    public int inflateInit(int w, @SuppressWarnings("rawtypes") Enum wrapperType) {
        istate = new Inflate();
        return istate.inflateInit(this, w, (WrapperType) wrapperType);
    }

    public int inflate(int f) {
        if (istate == null) {
            return JZlib.Z_STREAM_ERROR;
        }
        return istate.inflate(this, f);
    }

    public int inflateEnd() {
        if (istate == null) {
            return JZlib.Z_STREAM_ERROR;
        }
        int ret = istate.inflateEnd(this);
        istate = null;
        return ret;
    }

    public int inflateSync() {
        if (istate == null) {
            return JZlib.Z_STREAM_ERROR;
        }
        return istate.inflateSync(this);
    }

    public int inflateSetDictionary(byte[] dictionary, int dictLength) {
        if (istate == null) {
            return JZlib.Z_STREAM_ERROR;
        }
        return istate.inflateSetDictionary(this, dictionary, dictLength);
    }

    public int deflateInit(int level) {
        return deflateInit(level, JZlib.MAX_WBITS);
    }

    public int deflateInit(int level, Enum<?> wrapperType) {
        return deflateInit(level, JZlib.MAX_WBITS, wrapperType);
    }

    public int deflateInit(int level, int bits) {
        return deflateInit(level, bits, WrapperType.ZLIB);
    }

    public int deflateInit(int level, int bits, @SuppressWarnings("rawtypes") Enum wrapperType) {
        dstate = new Deflate();
        return dstate.deflateInit(this, level, bits, (WrapperType) wrapperType);
    }

    public int deflate(int flush) {
        if (dstate == null) {
            return JZlib.Z_STREAM_ERROR;
        }
        return dstate.deflate(this, flush);
    }

    public int deflateEnd() {
        if (dstate == null) {
            return JZlib.Z_STREAM_ERROR;
        }
        int ret = dstate.deflateEnd();
        dstate = null;
        return ret;
    }

    public int deflateParams(int level, int strategy) {
        if (dstate == null) {
            return JZlib.Z_STREAM_ERROR;
        }
        return dstate.deflateParams(this, level, strategy);
    }

    public int deflateSetDictionary(byte[] dictionary, int dictLength) {
        if (dstate == null) {
            return JZlib.Z_STREAM_ERROR;
        }
        return dstate.deflateSetDictionary(this, dictionary, dictLength);
    }

    // Flush as much pending output as possible. All deflate() output goes
    // through this function so some applications may wish to modify it
    // to avoid allocating a large strm->next_out buffer and copying into it.
    // (See also read_buf()).
    void flush_pending() {
        int len = dstate.pending;

        if (len > avail_out) {
            len = avail_out;
        }
        if (len == 0) {
            return;
        }

        if (dstate.pending_buf.length <= dstate.pending_out ||
                next_out.length <= next_out_index ||
                dstate.pending_buf.length < dstate.pending_out + len ||
                next_out.length < next_out_index + len) {
            System.out.println(dstate.pending_buf.length + ", " +
                    dstate.pending_out + ", " + next_out.length + ", " +
                    next_out_index + ", " + len);
            System.out.println("avail_out=" + avail_out);
        }

        System.arraycopy(dstate.pending_buf, dstate.pending_out, next_out,
                next_out_index, len);

        next_out_index += len;
        dstate.pending_out += len;
        total_out += len;
        avail_out -= len;
        dstate.pending -= len;
        if (dstate.pending == 0) {
            dstate.pending_out = 0;
        }
    }

    // Read a new buffer from the current input stream, update the adler32
    // and total number of bytes read.  All deflate() input goes through
    // this function so some applications may wish to modify it to avoid
    // allocating a large strm->next_in buffer and copying from it.
    // (See also flush_pending()).
    int read_buf(byte[] buf, int start, int size) {
        int len = avail_in;

        if (len > size) {
            len = size;
        }
        if (len == 0) {
            return 0;
        }

        avail_in -= len;

        switch (dstate.wrapperType) {
        case ZLIB:
            adler = Adler32.adler32(adler, next_in, next_in_index, len);
            break;
        case GZIP:
            crc32 = CRC32.crc32(crc32, next_in, next_in_index, len);
            break;
        }

        System.arraycopy(next_in, next_in_index, buf, start, len);
        next_in_index += len;
        total_in += len;
        return len;
    }

    public void free() {
        next_in = null;
        next_out = null;
        msg = null;
    }
}
