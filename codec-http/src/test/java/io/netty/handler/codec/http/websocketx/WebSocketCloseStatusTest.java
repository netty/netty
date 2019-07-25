/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

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
        Assert.assertEquals("1000 Bye", NORMAL_CLOSURE.toString());
    }

    @Test
    public void testKnownStatuses() {
        Assert.assertSame(NORMAL_CLOSURE, valueOf(1000));
        Assert.assertSame(ENDPOINT_UNAVAILABLE, valueOf(1001));
        Assert.assertSame(PROTOCOL_ERROR, valueOf(1002));
        Assert.assertSame(INVALID_MESSAGE_TYPE, valueOf(1003));
        Assert.assertSame(INVALID_PAYLOAD_DATA, valueOf(1007));
        Assert.assertSame(POLICY_VIOLATION, valueOf(1008));
        Assert.assertSame(MESSAGE_TOO_BIG, valueOf(1009));
        Assert.assertSame(MANDATORY_EXTENSION, valueOf(1010));
        Assert.assertSame(INTERNAL_SERVER_ERROR, valueOf(1011));
        Assert.assertSame(SERVICE_RESTART, valueOf(1012));
        Assert.assertSame(TRY_AGAIN_LATER, valueOf(1013));
        Assert.assertSame(BAD_GATEWAY, valueOf(1014));
    }

    @Test
    public void testNaturalOrder() {
        Assert.assertThat(PROTOCOL_ERROR, Matchers.greaterThan(NORMAL_CLOSURE));
        Assert.assertThat(PROTOCOL_ERROR, Matchers.greaterThan(valueOf(1001)));
        Assert.assertThat(PROTOCOL_ERROR, Matchers.comparesEqualTo(PROTOCOL_ERROR));
        Assert.assertThat(PROTOCOL_ERROR, Matchers.comparesEqualTo(valueOf(1002)));
        Assert.assertThat(PROTOCOL_ERROR, Matchers.lessThan(INVALID_MESSAGE_TYPE));
        Assert.assertThat(PROTOCOL_ERROR, Matchers.lessThan(valueOf(1007)));
    }

    @Test
    public void testUserDefinedStatuses() {
        // Given, when
        WebSocketCloseStatus feedTimeot = new WebSocketCloseStatus(6033, "Feed timed out");
        WebSocketCloseStatus untradablePrice = new WebSocketCloseStatus(6034, "Untradable price");

        // Then
        Assert.assertNotSame(feedTimeot, valueOf(6033));
        Assert.assertEquals(feedTimeot.code(), 6033);
        Assert.assertEquals(feedTimeot.reasonText(), "Feed timed out");

        Assert.assertNotSame(untradablePrice, valueOf(6034));
        Assert.assertEquals(untradablePrice.code(), 6034);
        Assert.assertEquals(untradablePrice.reasonText(), "Untradable price");
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
        Assert.assertEquals(0, invalidCodes.first().intValue());
        Assert.assertEquals(2999, invalidCodes.last().intValue());
        Assert.assertEquals(3000 - validCodes.size(), invalidCodes.size());

        invalidCodes.retainAll(knownCodes);
        Assert.assertEquals(invalidCodes, Collections.emptySet());
    }

}
