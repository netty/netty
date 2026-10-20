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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MemoryFileUploadTest {

    @Test
    final void testMemoryFileUploadEquals() {
        MemoryFileUpload f1 =
                new MemoryFileUpload("m1", "m1", "application/json", null, null, 100);
        assertEquals(f1, f1);
    }

    @ParameterizedTest
    @ValueSource(bytes = {
            0x00,
            HttpConstants.CR,
            HttpConstants.LF,
            0x19,
            HttpConstants.DEL,
            HttpConstants.DOUBLE_QUOTE,
            HttpConstants.BACKSLASH})
    void filenameCannotContainIllegalCharacters(byte illegal) {
        assertIllegalFilename(((char) illegal) + "f");
        assertIllegalFilename("f" + ((char) illegal) + "f");
        assertIllegalFilename("f" + ((char) illegal));
    }

    private static void assertIllegalFilename(String filename) {
        assertThatThrownBy(() -> new MemoryFileUpload("f", filename, "plain/text", null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Illegal filename character");
    }

    @ParameterizedTest
    @ValueSource(bytes = {
        0x00,
        HttpConstants.CR,
        HttpConstants.LF,
        0x19,
        HttpConstants.DEL})
    void contentTypeCannotContainIllegalCharacters(byte illegal) {
        assertIllegalContentType(((char) illegal) + "text/plain");
        assertIllegalContentType("text/plain" + ((char) illegal) + " charset=\"us-ascii\"");
        assertIllegalContentType("text/plain" + ((char) illegal));
    }

    @Test
    void contentTypeCannotStartWithSpaceOrTabCharacter() {
        assertIllegalContentType(" text/plain");
        assertIllegalContentType("\ttext/plain");
    }

    private static void assertIllegalContentType(String contentType) {
        assertThatThrownBy(() -> new MemoryFileUpload("f", "f.txt", contentType, null, null, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Illegal Content-Type character");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "text",
        "text;plain",
        "text;plain/octet-stream",
        "text; charset=\"utf/8\"; charset=us-ascii",
        "text/",
        "text/;",
        "/plain",
    })
    void contentTypeCannotHaveMalformedGrammar(String contentType) {
        assertMalformedContentType(contentType);
    }

    private static void assertMalformedContentType(String contentType) {
        assertThatThrownBy(() -> new MemoryFileUpload("f", "f.txt", contentType, null, null, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Malformed content type value");
    }

    @Test
    void contentTypeAllowsWhitespaceQuotesAndObsText() {
        assertValidContentType("text/plain; charset=\"us-ascii\"");
        assertValidContentType("text/plain;\tcharset=us-ascii");
        assertValidContentType("text/plain; name=\"\u00e9\"");
        assertValidContentType("t/p;");
        assertValidContentType("t/p; ");
        assertValidContentType("t/p");
    }

    private static void assertValidContentType(String contentType) {
        assertEquals(contentType, new MemoryFileUpload("f", "f.txt", contentType, null, null, 0).getContentType());
    }
}
