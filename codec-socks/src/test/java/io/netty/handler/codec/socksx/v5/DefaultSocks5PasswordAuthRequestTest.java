/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.socksx.v5;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class DefaultSocks5PasswordAuthRequestTest {
    @Test
    public void testConstructorParamsAreNotNull() {
        try {
            new DefaultSocks5PasswordAuthRequest(null, "");
        } catch (Exception e) {
            assertTrue(e instanceof NullPointerException);
        }
        try {
            new DefaultSocks5PasswordAuthRequest("", null);
        } catch (Exception e) {
            assertTrue(e instanceof NullPointerException);
        }
    }

    @Test
    public void testUsernameOrPasswordIsNotAscii() {
        try {
            new DefaultSocks5PasswordAuthRequest("παράδειγμα.δοκιμή", "password");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        try {
            new DefaultSocks5PasswordAuthRequest("username", "παράδειγμα.δοκιμή");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testUsernameOrPasswordLengthIsLessThan255Chars() {
        try {
            new DefaultSocks5PasswordAuthRequest(
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword",
                    "password");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        try {
            new DefaultSocks5PasswordAuthRequest("password",
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword" +
                    "passwordpasswordpasswordpasswordpasswordpasswordpassword");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
}
