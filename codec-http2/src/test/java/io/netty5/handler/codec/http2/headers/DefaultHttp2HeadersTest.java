/*
 * Copyright 2022 The Netty Project
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
package io.netty5.handler.codec.http2.headers;

import io.netty5.handler.codec.http.headers.AbstractHttpHeadersTest;
import io.netty5.handler.codec.http.headers.HeaderValidationException;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.handler.codec.http2.headers.Http2Headers.PseudoHeaderName;
import io.netty5.util.AsciiString;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultHttp2HeadersTest extends AbstractHttpHeadersTest {
    @Override
    protected Http2Headers newHeaders() {
        return Http2Headers.newHeaders();
    }

    @Override
    protected Http2Headers newHeaders(int initialSizeHint) {
        return Http2Headers.newHeaders(initialSizeHint, true, true, true);
    }

    @Override
    @Test
    public void invalidHeaderNameOutOfRangeCharacter() {
        final HttpHeaders headers = newHeaders();
        assertThrows(HeaderValidationException.class, () -> headers.add(String.valueOf((char) -1), "foo"));
    }

    @Override
    @Test
    public void invalidHeaderNameOutOfRangeCharacterAsciiString() {
        final HttpHeaders headers = newHeaders();
        assertThrows(HeaderValidationException.class, () ->
                headers.add(AsciiString.cached(String.valueOf((char) -1)), "foo"));
    }

    @Override
    @Test
    public void invalidHeaderNameCharacter() {
        final HttpHeaders headers = newHeaders();
        assertThrows(HeaderValidationException.class, () -> headers.add("=", "foo"));
    }

    @Override
    @Test
    public void invalidHeaderNameCharacterAsciiString() {
        final HttpHeaders headers = newHeaders();
        assertThrows(HeaderValidationException.class, () -> headers.add(AsciiString.cached("="), "foo"));
    }

    @Test
    void setAndGetPseudoHeaders() {
        Http2Headers headers = newHeaders();
        headers.method("get");
        headers.scheme("https");
        headers.authority("usr@host:8080");
        headers.path("/index");
        headers.status("200");
        assertThat(headers.method()).isEqualTo("get");
        assertThat(headers.scheme()).isEqualTo("https");
        assertThat(headers.authority()).isEqualTo("usr@host:8080");
        assertThat(headers.path()).isEqualTo("/index");
        assertThat(headers.status()).isEqualTo("200");
    }

    @Test
    void addAndGetPseudoHeaders() {
        Http2Headers headers = newHeaders();
        headers.add(":method", "get");
        headers.add(":scheme", "https");
        headers.add(":authority", "usr@host:8080");
        headers.add(":path", "/index");
        headers.add(":status", "200");
        assertThat(headers.method()).isEqualTo("get");
        assertThat(headers.scheme()).isEqualTo("https");
        assertThat(headers.authority()).isEqualTo("usr@host:8080");
        assertThat(headers.path()).isEqualTo("/index");
        assertThat(headers.status()).isEqualTo("200");
    }

    @Test
    void setAndGenericGetPseudoHeaders() {
        Http2Headers headers = newHeaders();
        headers.method("get");
        headers.scheme("https");
        headers.authority("usr@host:8080");
        headers.path("/index");
        headers.status("200");
        assertThat(headers.get(":method")).isEqualTo("get");
        assertThat(headers.get(":scheme")).isEqualTo("https");
        assertThat(headers.get(":authority")).isEqualTo("usr@host:8080");
        assertThat(headers.get(":path")).isEqualTo("/index");
        assertThat(headers.get(":status")).isEqualTo("200");
    }

    @Test
    void setOfPseudoHeadersMustOverwriteExistingValues() {
        Http2Headers headers = newHeaders();
        headers.add(":method", "1");
        headers.add(":scheme", "2");
        headers.add(":authority", "3");
        headers.add(":path", "4");
        headers.add(":status", "5");

        headers.method("get");
        headers.scheme("https");
        headers.authority("usr@host:8080");
        headers.path("/index");
        headers.status("200");
        assertThat(headers.method()).isEqualTo("get");
        assertThat(headers.scheme()).isEqualTo("https");
        assertThat(headers.authority()).isEqualTo("usr@host:8080");
        assertThat(headers.path()).isEqualTo("/index");
        assertThat(headers.status()).isEqualTo("200");
    }

    @RepeatedTest(100)
    void pseudoHeadersMustComeFirstInIterationOrder(RepetitionInfo info) {
        Http2Headers headers = newHeaders();
        String pseudoValue = "pseudo";
        String normalValue = "normal";

        ArrayList<Entry<CharSequence, CharSequence>> list = new ArrayList<>();
        list.add(Map.entry("some-header1", normalValue));
        list.add(Map.entry("some-header1", normalValue));
        list.add(Map.entry("some-header2", normalValue));
        list.add(Map.entry("some-header3", normalValue));
        list.add(Map.entry("some-header4", normalValue));
        list.add(Map.entry("some-header4", normalValue));
        list.add(Map.entry("some-header5", normalValue));
        list.add(Map.entry(PseudoHeaderName.METHOD.value(), pseudoValue));
        list.add(Map.entry(PseudoHeaderName.SCHEME.value(), pseudoValue));
        list.add(Map.entry(PseudoHeaderName.AUTHORITY.value(), pseudoValue));
        list.add(Map.entry(PseudoHeaderName.PATH.value(), pseudoValue));
        list.add(Map.entry(PseudoHeaderName.STATUS.value(), pseudoValue));
        list.add(Map.entry(PseudoHeaderName.PROTOCOL.value(), pseudoValue));
        Collections.shuffle(list, new Random(info.getCurrentRepetition()));

        for (Entry<CharSequence, CharSequence> entry : list) {
            headers.add(entry.getKey(), entry.getValue());
        }

        var itr = headers.iterator();

        var entry = itr.next(); // Pseudo-header 1
        assertThat(entry.getValue()).isEqualTo(pseudoValue);
        assertTrue(PseudoHeaderName.isPseudoHeader(entry.getKey()));
        entry = itr.next(); // Pseudo-header 2
        assertThat(entry.getValue()).isEqualTo(pseudoValue);
        assertTrue(PseudoHeaderName.isPseudoHeader(entry.getKey()));
        entry = itr.next(); // Pseudo-header 3
        assertThat(entry.getValue()).isEqualTo(pseudoValue);
        assertTrue(PseudoHeaderName.isPseudoHeader(entry.getKey()));
        entry = itr.next(); // Pseudo-header 4
        assertThat(entry.getValue()).isEqualTo(pseudoValue);
        assertTrue(PseudoHeaderName.isPseudoHeader(entry.getKey()));
        entry = itr.next(); // Pseudo-header 5
        assertThat(entry.getValue()).isEqualTo(pseudoValue);
        assertTrue(PseudoHeaderName.isPseudoHeader(entry.getKey()));
        entry = itr.next(); // Pseudo-header 6
        assertThat(entry.getValue()).isEqualTo(pseudoValue);
        assertTrue(PseudoHeaderName.isPseudoHeader(entry.getKey()));

        assertThat(itr.next().getValue()).isEqualTo(normalValue); // Normal header 1
        assertThat(itr.next().getValue()).isEqualTo(normalValue); // Normal header 2
        assertThat(itr.next().getValue()).isEqualTo(normalValue); // Normal header 3
        assertThat(itr.next().getValue()).isEqualTo(normalValue); // Normal header 4
        assertThat(itr.next().getValue()).isEqualTo(normalValue); // Normal header 5
        assertThat(itr.next().getValue()).isEqualTo(normalValue); // Normal header 6
        assertThat(itr.next().getValue()).isEqualTo(normalValue); // Normal header 7
        assertFalse(itr.hasNext());
    }
}
