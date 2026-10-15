/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultHttpHeadersFactoryTest {

    @ParameterizedTest
    @ValueSource(strings = {"content-length", "Transfer-Encoding", "trailer"})
    void trailersFactoryRejectsProhibitedTrailers(String name) {
        assertProhibited(DefaultHttpHeadersFactory.trailersFactory(), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"content-length", "Transfer-Encoding", "trailer"})
    void trailersFactoryWithValidationRejectsProhibitedTrailers(String name) {
        DefaultHttpHeadersFactory trailersFactory = DefaultHttpHeadersFactory.trailersFactory();
        assertSame(trailersFactory, trailersFactory.withValidation(true));
        assertProhibited(trailersFactory.withValidation(true), name);
        assertProhibited(trailersFactory.withNameValidation(true), name);
        assertProhibited(trailersFactory.withValueValidation(false), name);
        assertProhibited(trailersFactory.withCombiningHeaders(true), name);
        assertProhibited(trailersFactory.withValidation(false).withValidation(true), name);
        assertProhibited(trailersFactory.withNameValidation(false).withNameValidation(true), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"content-length", "Transfer-Encoding", "trailer"})
    void validatedLastHttpContentRejectsProhibitedTrailers(final String name) {
        final DefaultLastHttpContent content = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER,
                DefaultHttpHeadersFactory.trailersFactory().withValidation(true));
        assertThrows(IllegalArgumentException.class, () -> content.trailingHeaders().add(name, "1"));
    }

    @Test
    void trailersFactoryWithoutValidationAllowsProhibitedTrailers() {
        DefaultHttpHeadersFactory factory = DefaultHttpHeadersFactory.trailersFactory().withValidation(false);
        assertFalse(factory.isValidatingHeaderNames());
        final HttpHeaders headers = factory.newHeaders();
        assertDoesNotThrow(() -> headers.add("content-length", "1"));
    }

    @Test
    void headersFactoryAllowsContentLength() {
        DefaultHttpHeadersFactory factory = DefaultHttpHeadersFactory.headersFactory();
        assertSame(factory, factory.withValidation(false).withValidation(true));
        final HttpHeaders headers = factory.withNameValidation(false).withNameValidation(true).newHeaders();
        assertDoesNotThrow(() -> headers.add("content-length", "1"));
    }

    private static void assertProhibited(DefaultHttpHeadersFactory factory, final String name) {
        final HttpHeaders headers = factory.newHeaders();
        assertThrows(IllegalArgumentException.class, () -> headers.add(name, "1"));
    }
}
