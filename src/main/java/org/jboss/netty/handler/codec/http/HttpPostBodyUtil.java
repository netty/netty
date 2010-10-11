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
package org.jboss.netty.handler.codec.http;

import java.nio.charset.Charset;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.util.CharsetUtil;

/**
 * Shared Static object between HttpMessageDecoder, HttpPostRequestDecoder and HttpPostRequestEncoder
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://openr66.free.fr/">Frederic Bregier</a>
 *
 */
public class HttpPostBodyUtil {
    public static int chunkSize = 8096;
    /**
     * HTTP content disposition header name.
     */
    public static final String CONTENT_DISPOSITION = "Content-Disposition";

    public static final String NAME = "name";

    public static final String FILENAME = "filename";

    /**
     * Content-disposition value for form data.
     */
    public static final String FORM_DATA = "form-data";

    /**
     * Content-disposition value for file attachment.
     */
    public static final String ATTACHMENT = "attachment";

    /**
     * Content-disposition value for file attachment.
     */
    public static final String FILE = "file";

    /**
     * HTTP content type body attribute for multiple uploads.
     */
    public static final String MULTIPART_MIXED = "multipart/mixed";

    /**
     * Charset for 8BIT
     */
    public static final Charset ISO_8859_1 = CharsetUtil.ISO_8859_1;

    /**
     * Charset for 7BIT
     */
    public static final Charset US_ASCII = CharsetUtil.US_ASCII;

    /**
     * Default Content-Type in binary form
     */
    public static final String DEFAULT_BINARY_CONTENT_TYPE = "application/octet-stream";

    /**
     * Default Content-Type in Text form
     */
    public static final String DEFAULT_TEXT_CONTENT_TYPE = "text/plain";

    /**
     * Allowed mechanism for multipart
     * mechanism := "7bit"
                  / "8bit"
                  / "binary"
       Not allowed: "quoted-printable"
                  / "base64"
     */
    public static enum TransferEncodingMechanism {
        /**
         * Default encoding
         */
        BIT7("7bit"),
        /**
         * Short lines but not in ASCII - no encoding
         */
        BIT8("8bit"),
        /**
         * Could be long text not in ASCII - no encoding
         */
        BINARY("binary");

        public String value;

        private TransferEncodingMechanism(String value) {
            this.value = value;
        }

        private TransferEncodingMechanism() {
            value = name();
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private HttpPostBodyUtil() {
        super();
    }

    //Some commons methods between HttpPostRequestDecoder and HttpMessageDecoder
    /**
     * Skip control Characters
     * @param buffer
     */
    static void skipControlCharacters(ChannelBuffer buffer) {
        for (;;) {
            char c = (char) buffer.readUnsignedByte();
            if (!Character.isISOControl(c) && !Character.isWhitespace(c)) {
                buffer.readerIndex(buffer.readerIndex() - 1);
                break;
            }
        }
    }

    /**
     * Find the first non whitespace
     * @param sb
     * @param offset
     * @return the rank of the first non whitespace
     */
    static int findNonWhitespace(String sb, int offset) {
        int result;
        for (result = offset; result < sb.length(); result ++) {
            if (!Character.isWhitespace(sb.charAt(result))) {
                break;
            }
        }
        return result;
    }

    /**
     * Find the first whitespace
     * @param sb
     * @param offset
     * @return the rank of the first whitespace
     */
    static int findWhitespace(String sb, int offset) {
        int result;
        for (result = offset; result < sb.length(); result ++) {
            if (Character.isWhitespace(sb.charAt(result))) {
                break;
            }
        }
        return result;
    }

    /**
     * Find the end of String
     * @param sb
     * @return the rank of the end of string
     */
    static int findEndOfString(String sb) {
        int result;
        for (result = sb.length(); result > 0; result --) {
            if (!Character.isWhitespace(sb.charAt(result - 1))) {
                break;
            }
        }
        return result;
    }

}
