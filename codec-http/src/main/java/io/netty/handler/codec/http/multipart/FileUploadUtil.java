/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.http.multipart;

import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpHeaderValidationUtil;
import io.netty.util.internal.ObjectUtil;

final class FileUploadUtil {

    private FileUploadUtil() { }

    static int hashCode(FileUpload upload) {
        return upload.getName().hashCode();
    }

    static boolean equals(FileUpload upload1, FileUpload upload2) {
        return upload1.getName().equalsIgnoreCase(upload2.getName());
    }

    static int compareTo(FileUpload upload1, FileUpload upload2) {
        return upload1.getName().compareToIgnoreCase(upload2.getName());
    }

    /**
     * Control characters, the DEL character, double-quote, and backslash are either disallowed or strongly discouraged,
     * depending on which {@code multipart/form-data} specification you read.
     * This method conservatively rejects all of them, and is used for <em>outbound</em> (encoding) filenames.
     * @param filename The filename to check.
     * @return The validated filename, unchanged.
     */
    static String validateFileNameForMultiPart(String filename) {
        int length = ObjectUtil.checkNotNull(filename, "filename").length();
        for (int i = 0; i < length; i++) {
            char c = filename.charAt(i);
            if (c < HttpConstants.SP /*control character block*/ || c == HttpConstants.DEL ||
                c == HttpConstants.DOUBLE_QUOTE || c == HttpConstants.BACKSLASH) {
                throw new IllegalArgumentException(
                    String.format("Illegal filename character 0x%02x at index %d", (int) c, i));
            }
        }
        return filename;
    }

    /**
     * Control characters and the DEL character are either disallowed or strongly discouraged in the
     * {@code Content-Type}.
     * Space and tab is allowed except for the first character, quotation and other special characters are allowed.
     * <p>
     * The Content-Type value MUST be structured in the form of {@code type/subtype parameters}.
     * See <a href="https://www.rfc-editor.org/rfc/rfc9110.html#section-8.3.1">RFC 9110 §8.3.1</a>,
     * and <a href="https://www.rfc-editor.org/info/rfc2045/#section-5.1">RFC 2045 §5.1</a>.
     * <p>
     * This is used for multipart <em>encoding</em>, so we refuse to emit a malformed content type.
     *
     * @param contentType The Content-Type header value to check.
     * @return The valid Content-Type header value, unchanged.
     */
    static String validateContentTypeForMultiPart(String contentType) {
        checkContentTypeForMultiPart(
            ObjectUtil.checkNotNull(contentType, "contentType"), true);
        return contentType;
    }

    /**
     * Determine if the given content type value is syntactically valid and well-formed.
     * <p>
     * The Content-Type value is valid if it is structured in the form of {@code type/subtype parameters}.
     * See <a href="https://www.rfc-editor.org/rfc/rfc9110.html#section-8.3.1">RFC 9110 §8.3.1</a>,
     * and <a href="https://www.rfc-editor.org/info/rfc2045/#section-5.1">RFC 2045 §5.1</a>.
     * <p>
     * This is used for multipart <em>decoding</em>, where
     * <a href="https://www.rfc-editor.org/info/rfc2045/#section-5.2">RFC 2045 §5.2</a>
     * recommends that syntactically invalid content type values be replaced with a default.
     *
     * @param contentType The Content-Type header value to check.
     * @return {@code true} if the Content-Type header value is valid, otherwise {@code false}.
     */
    static boolean isContentTypeForMultiPartValid(String contentType) {
        return contentType != null && checkContentTypeForMultiPart(contentType, false);
    }

    private static boolean checkContentTypeForMultiPart(String contentType, boolean throwIfInvalid) {
        int index = HttpHeaderValidationUtil.validateValidHeaderValue(contentType);
        if (index != -1) {
            if (throwIfInvalid) {
                throw new IllegalArgumentException(
                    String.format("Illegal Content-Type character 0x%02x at index %d",
                        (int) contentType.charAt(index), index));
            }
            return false;
        }

        int slash = contentType.indexOf('/');
        int semicolon = contentType.indexOf(';');
        if (slash == -1 ||
            slash == 0 ||
            (semicolon == -1 && slash == contentType.length() - 1) ||
            (semicolon != -1 && slash + 1 >= semicolon)) {
            if (throwIfInvalid) {
                throw new IllegalArgumentException("Malformed content type value: " +
                    "index of '/' is " + slash + " content type length " + contentType.length());
            }
            return false;
        }
        return true;
    }
}
