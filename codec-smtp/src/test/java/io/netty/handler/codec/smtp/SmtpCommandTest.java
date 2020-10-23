/*
 * Copyright 2017 The Netty Project
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

import org.junit.Test;

import static org.junit.Assert.*;

public class SmtpCommandTest {
    @Test
    public void getCommandFromCache() {
        assertSame(SmtpCommand.DATA, SmtpCommand.valueOf("DATA"));
        assertSame(SmtpCommand.EHLO, SmtpCommand.valueOf("EHLO"));
        assertNotSame(SmtpCommand.EHLO, SmtpCommand.valueOf("ehlo"));
    }

    @Test
    public void equalsIgnoreCase() {
        assertEquals(SmtpCommand.MAIL, SmtpCommand.valueOf("mail"));
        assertEquals(SmtpCommand.valueOf("test"), SmtpCommand.valueOf("TEST"));
    }

    @Test
    public void isContentExpected() {
        assertTrue(SmtpCommand.valueOf("DATA").isContentExpected());
        assertTrue(SmtpCommand.valueOf("data").isContentExpected());

        assertFalse(SmtpCommand.HELO.isContentExpected());
        assertFalse(SmtpCommand.HELP.isContentExpected());
        assertFalse(SmtpCommand.valueOf("DATA2").isContentExpected());
    }
}
