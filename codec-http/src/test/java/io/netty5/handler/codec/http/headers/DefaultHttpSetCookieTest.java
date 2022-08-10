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
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty5.handler.codec.http.headers;

import io.netty5.handler.codec.http.headers.HttpSetCookie.SameSite;
import io.netty5.util.AsciiString;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultHttpSetCookieTest {

    @Test
    void testEqual() {
        assertThat(new DefaultHttpSetCookie("foo", "bar")).isEqualTo(new DefaultHttpSetCookie("foo", "bar"));
        assertThat(new DefaultHttpSetCookie("foo", "bar")).hasSameHashCodeAs(new DefaultHttpSetCookie("foo", "bar"));

        assertThat(new DefaultHttpSetCookie("foo", "bar")).isEqualTo(
                new DefaultHttpSetCookie(AsciiString.cached("foo"), AsciiString.cached("bar")));
        assertThat(new DefaultHttpSetCookie("foo", "bar")).hasSameHashCodeAs(
                new DefaultHttpSetCookie(AsciiString.cached("foo"), AsciiString.cached("bar")));

        // Domain is case-insensitive, other attributes are ignored:
        assertThat(new DefaultHttpSetCookie(
                "foo", "bar", "/", "servicetalk.io", null, 1L, SameSite.None, true, false, true)
        ).isEqualTo(new DefaultHttpSetCookie(
                "foo", "bar", "/", "ServiceTalk.io", null, 2L, SameSite.Lax, false, true, false));

        assertThat(new DefaultHttpSetCookie(
                "foo", "bar", "/", "servicetalk.io", null, 1L, SameSite.None, true, false, true)
        ).hasSameHashCodeAs(new DefaultHttpSetCookie(
                        "foo", "bar", "/", "ServiceTalk.io", null, 2L, SameSite.Lax, false, true, false));
    }

    @Test
    void testNotEqual() {
        // Name is case-sensitive:
        assertThat(new DefaultHttpSetCookie("foo", "bar")).isNotEqualTo(new DefaultHttpSetCookie("Foo", "bar"));
        assertThat(new DefaultHttpSetCookie("foo", "bar").hashCode()).isNotEqualTo(
                new DefaultHttpSetCookie("fooo", "bar").hashCode());

        // Value is case-sensitive:
        assertThat(new DefaultHttpSetCookie("foo", "bar")).isNotEqualTo(new DefaultHttpSetCookie("foo", "Bar"));
        assertThat(new DefaultHttpSetCookie("foo", "bar").hashCode()).isNotEqualTo(
                new DefaultHttpSetCookie("foo", "barr").hashCode());

        // Path is case-sensitive:
        assertThat(new DefaultHttpSetCookie("foo", "bar", "/path", "servicetalk.io",
                        null, 1L, SameSite.None, true, false, true)
        ).isNotEqualTo(new DefaultHttpSetCookie("foo", "bar", "/Path", "servicetalk.io",
                                                null, 1L, SameSite.None, true, false, true));

        assertThat(new DefaultHttpSetCookie("foo", "bar", "/path", "servicetalk.io",
                        null, 1L, SameSite.None, true, false, true).hashCode()
        ).isNotEqualTo(new DefaultHttpSetCookie("foo", "bar", "/pathh", "servicetalk.io",
                                                null, 1L, SameSite.None, true, false, true).hashCode());

        // Domain doesn't match:
        assertThat(new DefaultHttpSetCookie("foo", "bar", "/path", "servicetalk.io",
                        null, 1L, SameSite.None, true, false, true)
        ).isNotEqualTo(new DefaultHttpSetCookie("foo", "bar", "/path", "docs.servicetalk.io",
                                                null, 1L, SameSite.None, true, false, true));

        assertThat(new DefaultHttpSetCookie("foo", "bar", "/path", "servicetalk.io",
                        null, 1L, SameSite.None, true, false, true).hashCode()
        ).isNotEqualTo(new DefaultHttpSetCookie("foo", "bar", "/path", "docs.servicetalk.io",
                                                null, 1L, SameSite.None, true, false, true).hashCode());
    }
}
