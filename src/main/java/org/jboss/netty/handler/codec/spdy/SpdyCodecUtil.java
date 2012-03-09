/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.buffer.ChannelBuffer;

final class SpdyCodecUtil {

    static final int SPDY_VERSION = 2;

    static final int SPDY_HEADER_TYPE_OFFSET   = 2;
    static final int SPDY_HEADER_FLAGS_OFFSET  = 4;
    static final int SPDY_HEADER_LENGTH_OFFSET = 5;
    static final int SPDY_HEADER_SIZE          = 8;

    static final int SPDY_MAX_LENGTH = 0xFFFFFF; // Length is a 24-bit field

    static final byte SPDY_DATA_FLAG_FIN      = 0x01;
    static final byte SPDY_DATA_FLAG_COMPRESS = 0x02;

    static final int SPDY_SYN_STREAM_FRAME    = 1;
    static final int SPDY_SYN_REPLY_FRAME     = 2;
    static final int SPDY_RST_STREAM_FRAME    = 3;
    static final int SPDY_SETTINGS_FRAME      = 4;
    static final int SPDY_NOOP_FRAME          = 5;
    static final int SPDY_PING_FRAME          = 6;
    static final int SPDY_GOAWAY_FRAME        = 7;
    static final int SPDY_HEADERS_FRAME       = 8;
    static final int SPDY_WINDOW_UPDATE_FRAME = 9;

    static final byte SPDY_FLAG_FIN            = 0x01;
    static final byte SPDY_FLAG_UNIDIRECTIONAL = 0x02;

    static final byte SPDY_SETTINGS_CLEAR         = 0x01;
    static final byte SPDY_SETTINGS_PERSIST_VALUE = 0x01;
    static final byte SPDY_SETTINGS_PERSISTED     = 0x02;

    static final int SPDY_SETTINGS_MAX_ID = 0xFFFFFF; // ID is a 24-bit field

    static final int SPDY_MAX_NV_LENGTH = 0xFFFF; // Length is a 16-bit field

    // Zlib Dictionary
    private static final String SPDY_DICT_S =
        "optionsgetheadpostputdeletetraceacceptaccept-charsetaccept-encodingaccept-" +
        "languageauthorizationexpectfromhostif-modified-sinceif-matchif-none-matchi" +
        "f-rangeif-unmodifiedsincemax-forwardsproxy-authorizationrangerefererteuser" +
        "-agent10010120020120220320420520630030130230330430530630740040140240340440" +
        "5406407408409410411412413414415416417500501502503504505accept-rangesageeta" +
        "glocationproxy-authenticatepublicretry-afterservervarywarningwww-authentic" +
        "ateallowcontent-basecontent-encodingcache-controlconnectiondatetrailertran" +
        "sfer-encodingupgradeviawarningcontent-languagecontent-lengthcontent-locati" +
        "oncontent-md5content-rangecontent-typeetagexpireslast-modifiedset-cookieMo" +
        "ndayTuesdayWednesdayThursdayFridaySaturdaySundayJanFebMarAprMayJunJulAugSe" +
        "pOctNovDecchunkedtext/htmlimage/pngimage/jpgimage/gifapplication/xmlapplic" +
        "ation/xhtmltext/plainpublicmax-agecharset=iso-8859-1utf-8gzipdeflateHTTP/1" +
        ".1statusversionurl ";
    static final byte[] SPDY_DICT;
    static {
        byte[] SPDY_DICT_ = null;

        try {
            SPDY_DICT_ = SPDY_DICT_S.getBytes("US-ASCII");
            // dictionary is null terminated
            SPDY_DICT_[SPDY_DICT_.length - 1] = (byte) 0;
        } catch (Exception e) {
            SPDY_DICT_ = new byte[1];
        }

        SPDY_DICT = SPDY_DICT_;
    }


    private SpdyCodecUtil() {
    }


    /**
     * Reads a big-endian unsigned short integer from the buffer.
     */
    static int getUnsignedShort(ChannelBuffer buf, int offset) {
        return (int) ((buf.getByte(offset)     & 0xFF) << 8 |
                      (buf.getByte(offset + 1) & 0xFF));
    }

    /**
     * Reads a big-endian unsigned medium integer from the buffer.
     */
    static int getUnsignedMedium(ChannelBuffer buf, int offset) {
        return (int) ((buf.getByte(offset)     & 0xFF) << 16 |
                      (buf.getByte(offset + 1) & 0xFF) <<  8 |
                      (buf.getByte(offset + 2) & 0xFF));
    }

    /**
     * Reads a big-endian (31-bit) integer from the buffer.
     */
    static int getUnsignedInt(ChannelBuffer buf, int offset) {
        return (int) ((buf.getByte(offset)     & 0x7F) << 24 |
                      (buf.getByte(offset + 1) & 0xFF) << 16 |
                      (buf.getByte(offset + 2) & 0xFF) <<  8 |
                      (buf.getByte(offset + 3) & 0xFF));
    }

    /**
     * Reads a big-endian signed integer from the buffer.
     */
    static int getSignedInt(ChannelBuffer buf, int offset) {
        return (int) ((buf.getByte(offset)     & 0xFF) << 24 |
                      (buf.getByte(offset + 1) & 0xFF) << 16 |
                      (buf.getByte(offset + 2) & 0xFF) <<  8 |
                      (buf.getByte(offset + 3) & 0xFF));
    }

    /**
     * Returns {@code true} if ID is for a server initiated stream or ping.
     */
    static boolean isServerID(int ID) {
        // Server initiated streams and pings have even IDs
        return ID % 2 == 0;
    }

    /**
     * Validate a SPDY header name.
     */
    static void validateHeaderName(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (name.length() == 0) {
            throw new IllegalArgumentException(
                    "name cannot be length zero");
        }
        // Since name may only contain ascii characters, for valid names
        // name.length() returns the number of bytes when UTF-8 encoded.
        if (name.length() > SPDY_MAX_NV_LENGTH) {
            throw new IllegalArgumentException(
                    "name exceeds allowable length: " + name);
        }
        for (int i = 0; i < name.length(); i ++) {
            char c = name.charAt(i);
            if (c == 0) {
                throw new IllegalArgumentException(
                        "name contains null character: " + name);
            }
            if (c > 127) {
                throw new IllegalArgumentException(
                        "name contains non-ascii character: " + name);
            }
        }
    }

    /**
     * Validate a SPDY header value. Does not validate max length.
     */
    static void validateHeaderValue(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        if (value.length() == 0) {
            throw new IllegalArgumentException(
                    "value cannot be length zero");
        }
        for (int i = 0; i < value.length(); i ++) {
            char c = value.charAt(i);
            if (c == 0) {
                throw new IllegalArgumentException(
                        "value contains null character: " + value);
            }
        }
    }
}
