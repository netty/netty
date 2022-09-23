/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty5.handler.codec.http.headers;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.invoke.VarHandle;

import static io.netty5.handler.codec.http.headers.DefaultHttpSetCookiesTest.quotesInValuePreserved;
import static io.netty5.handler.codec.http.headers.HttpHeaders.newHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class DefaultHttpSetCookiesPedanticTest {
    @BeforeAll
    public static void enablePedantic() {
        HeaderUtils.cookieParsingStrictRfc6265(true);
        VarHandle.fullFence();
    }

    @AfterAll
    public static void disablePedantic() {
        HeaderUtils.cookieParsingStrictRfc6265(false);
        VarHandle.fullFence();
    }

    @Test
    void throwIfNoSpaceBeforeCookieAttributeValue() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "first=12345;Extension");
        headers.add("set-cookie", "second=12345;Expires=Mon, 22 Aug 2022 20:12:35 GMT");
        headers.add("set-cookie", "third=\"12345\";Expires=Mon, 22 Aug 2022 20:12:35 GMT");
        throwIfNoSpaceBeforeCookieAttributeValue(headers);
    }

    private static void throwIfNoSpaceBeforeCookieAttributeValue(HttpHeaders headers) {
        Exception exception;

        exception = assertThrows(IllegalArgumentException.class, () -> headers.getSetCookie("first"));
        assertThat(exception)
                .hasMessageContaining("first")
                .hasMessageContaining("space is required after ;");

        exception = assertThrows(IllegalArgumentException.class, () -> headers.getSetCookie("second"));
        assertThat(exception)
                .hasMessageContaining("second")
                .hasMessageContaining("space is required after ;");

        exception = assertThrows(IllegalArgumentException.class, () -> headers.getSetCookie("third"));
        assertThat(exception)
                .hasMessageContaining("third")
                .hasMessageContaining("space is required after ;");
    }

    @Test
    void spaceAfterQuotedValue() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie",
                "qwerty=\"12345\"; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        quotesInValuePreserved(headers);
    }
}
