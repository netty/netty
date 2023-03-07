/*
 * Copyright 2023 The Netty Project
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
package io.netty.channel.unix;

import io.netty.util.internal.PlatformDependent;

/**
 * struct cmsghdr {
 *     socklen_t cmsg_len;    // data byte count, including header
 *     int cmsg_level;  //originating protocol
 *     int cmsg_type;   // protocol-specific type
 *     // followed by unsigned char cmsg_data[];
 * };
 */
final class CmsgHdr {

    static final int SIZEOF_SIZE_T = NativeStaticallyReferencedJniMethods.sizeofSizeT();
    static final int CMSG_OFFSETOF_CMSG_LEN = NativeStaticallyReferencedJniMethods.cmsghdrOffsetofCmsgLen();
    static final int CMSG_OFFSETOF_CMSG_LEVEL = NativeStaticallyReferencedJniMethods.cmsghdrOffsetofCmsgLevel();
    static final int CMSG_OFFSETOF_CMSG_TYPE = NativeStaticallyReferencedJniMethods.cmsghdrOffsetofCmsgType();

    private CmsgHdr() { }

    static void write(long cmsghdrAddress, long cmsgHdrDataAddress,
                      int cmsgLen, int cmsgLevel, int cmsgType, short segmentSize) {
        if (SIZEOF_SIZE_T == 4) {
            PlatformDependent.putInt(cmsghdrAddress + CMSG_OFFSETOF_CMSG_LEN, cmsgLen);
        } else {
            assert SIZEOF_SIZE_T == 8;
            PlatformDependent.putLong(cmsghdrAddress + CMSG_OFFSETOF_CMSG_LEN, cmsgLen);
        }
        PlatformDependent.putInt(cmsghdrAddress + CMSG_OFFSETOF_CMSG_LEVEL, cmsgLevel);
        PlatformDependent.putInt(cmsghdrAddress + CMSG_OFFSETOF_CMSG_TYPE, cmsgType);
        PlatformDependent.putShort(cmsgHdrDataAddress, segmentSize);
    }
}
