/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package io.netty.handler.codec.http.websocketx;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.assertj.core.api.ThrowableAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import static io.netty.handler.codec.http.websocketx.WebSocketCloseStatus.*;

public class WebSocketCloseStatusTest {

    private final List<WebSocketCloseStatus> validCodes = Arrays.asList(
        NORMAL_CLOSURE,
        ENDPOINT_UNAVAILABLE,
        PROTOCOL_ERROR,
        INVALID_MESSAGE_TYPE,
        INVALID_PAYLOAD_DATA,
        POLICY_VIOLATION,
        MESSAGE_TOO_BIG,
        MANDATORY_EXTENSION,
        INTERNAL_SERVER_ERROR,
        SERVICE_RESTART,
        TRY_AGAIN_LATER,
        BAD_GATEWAY
    );

    @Test
    public void testToString() {
        assertEquals("1000 Bye", NORMAL_CLOSURE.toString());
    }

    @Test
    public void testKnownStatuses() {
        assertSame(NORMAL_CLOSURE, valueOf(1000));
        assertSame(ENDPOINT_UNAVAILABLE, valueOf(1001));
        assertSame(PROTOCOL_ERROR, valueOf(1002));
        assertSame(INVALID_MESSAGE_TYPE, valueOf(1003));
        assertSame(EMPTY, valueOf(1005));
        assertSame(ABNORMAL_CLOSURE, valueOf(1006));
        assertSame(INVALID_PAYLOAD_DATA, valueOf(1007));
        assertSame(POLICY_VIOLATION, valueOf(1008));
        assertSame(MESSAGE_TOO_BIG, valueOf(1009));
        assertSame(MANDATORY_EXTENSION, valueOf(1010));
        assertSame(INTERNAL_SERVER_ERROR, valueOf(1011));
        assertSame(SERVICE_RESTART, valueOf(1012));
        assertSame(TRY_AGAIN_LATER, valueOf(1013));
        assertSame(BAD_GATEWAY, valueOf(1014));
        assertSame(TLS_HANDSHAKE_FAILED, valueOf(1015));
    }

    @Test
    public void testNaturalOrder() {
        assertThat(PROTOCOL_ERROR, Matchers.greaterThan(NORMAL_CLOSURE));
        assertThat(PROTOCOL_ERROR, Matchers.greaterThan(valueOf(1001)));
        assertThat(PROTOCOL_ERROR, Matchers.comparesEqualTo(PROTOCOL_ERROR));
        assertThat(PROTOCOL_ERROR, Matchers.comparesEqualTo(valueOf(1002)));
        assertThat(PROTOCOL_ERROR, Matchers.lessThan(INVALID_MESSAGE_TYPE));
        assertThat(PROTOCOL_ERROR, Matchers.lessThan(valueOf(1007)));
    }

    @Test
    public void testUserDefinedStatuses() {
        // Given, when
        WebSocketCloseStatus feedTimeot = new WebSocketCloseStatus(6033, "Feed timed out");
        WebSocketCloseStatus untradablePrice = new WebSocketCloseStatus(6034, "Untradable price");

        // Then
        assertNotSame(feedTimeot, valueOf(6033));
        assertEquals(feedTimeot.code(), 6033);
        assertEquals(feedTimeot.reasonText(), "Feed timed out");

        assertNotSame(untradablePrice, valueOf(6034));
        assertEquals(untradablePrice.code(), 6034);
        assertEquals(untradablePrice.reasonText(), "Untradable price");
    }

    @Test
    public void testRfc6455CodeValidation() {
        // Given
        List<Integer> knownCodes = Arrays.asList(
            NORMAL_CLOSURE.code(),
            ENDPOINT_UNAVAILABLE.code(),
            PROTOCOL_ERROR.code(),
            INVALID_MESSAGE_TYPE.code(),
            INVALID_PAYLOAD_DATA.code(),
            POLICY_VIOLATION.code(),
            MESSAGE_TOO_BIG.code(),
            MANDATORY_EXTENSION.code(),
            INTERNAL_SERVER_ERROR.code(),
            SERVICE_RESTART.code(),
            TRY_AGAIN_LATER.code(),
            BAD_GATEWAY.code()
        );

        SortedSet<Integer> invalidCodes = new TreeSet<Integer>();

        // When
        for (int statusCode = Short.MIN_VALUE; statusCode < Short.MAX_VALUE; statusCode++) {
            if (!isValidStatusCode(statusCode)) {
                invalidCodes.add(statusCode);
            }
        }

        // Then
        assertEquals(0, invalidCodes.first().intValue());
        assertEquals(2999, invalidCodes.last().intValue());
        assertEquals(3000 - validCodes.size(), invalidCodes.size());

        invalidCodes.retainAll(knownCodes);
        assertEquals(invalidCodes, Collections.emptySet());
    }

    @Test
    public void testValidationEnabled() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(new ThrowableAssert.ThrowingCallable() {

                    @Override
                    public void call() throws RuntimeException {
                        new WebSocketCloseStatus(1006, "validation disabled");
                    }
                });
    }

    @Test
    public void testValidationDisabled() {
        WebSocketCloseStatus status = new WebSocketCloseStatus(1006, "validation disabled", false);
        assertEquals(1006, status.code());
        assertEquals("validation disabled", status.reasonText());
    }
}
