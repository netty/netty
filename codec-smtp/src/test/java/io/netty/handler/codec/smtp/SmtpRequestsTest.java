/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.smtp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class SmtpRequestsTest {
    @Test
    public void testSmtpInjectionWithCarriageReturn() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                SmtpRequests.mail("test@example.com\rQUIT");
            }
        });
    }

    @Test
    public void testSmtpInjectionWithLineFeed() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                SmtpRequests.mail("test@example.com\nQUIT");
            }
        });
    }

    @Test
    public void testSmtpInjectionWithCRLF() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                SmtpRequests.rcpt("test@example.com\r\nQUIT");
            }
        });
    }

    @Test
    public void testSmtpInjectionInAuthParameter() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                SmtpRequests.auth("PLAIN", "dGVzdA\rQUIT");
            }
        });
    }

    @Test
    public void testSmtpInjectionInHelo() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                SmtpRequests.helo("localhost\r\nQUIT");
            }
        });
    }
}
