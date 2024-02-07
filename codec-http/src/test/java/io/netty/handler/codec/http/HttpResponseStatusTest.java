/*
 * Copyright 2018 The Netty Project
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

import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.netty.handler.codec.http.HttpResponseStatus.parseLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class HttpResponseStatusTest {
    @Test
    public void parseLineStringJustCode() {
        assertSame(HttpResponseStatus.OK, parseLine("200"));
    }

    @Test
    public void parseLineStringCodeAndPhrase() {
        assertSame(HttpResponseStatus.OK, parseLine("200 OK"));
    }

    @Test
    public void parseLineStringCustomCode() {
        HttpResponseStatus customStatus = parseLine("612");
        assertEquals(612, customStatus.code());
    }

    @Test
    public void parseLineStringCustomCodeAndPhrase() {
        HttpResponseStatus customStatus = parseLine("612 FOO");
        assertEquals(612, customStatus.code());
        assertEquals("FOO", customStatus.reasonPhrase());
    }

    @Test
    public void parseLineStringMalformedCode() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                parseLine("200a");
            }
        });
    }

    @Test
    public void parseLineStringMalformedCodeWithPhrase() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                parseLine("200a foo");
            }
        });
    }

    @Test
    public void parseLineAsciiStringJustCode() {
        assertSame(HttpResponseStatus.OK, parseLine(new AsciiString("200")));
    }

    @Test
    public void parseLineAsciiStringCodeAndPhrase() {
        assertSame(HttpResponseStatus.OK, parseLine(new AsciiString("200 OK")));
    }

    @Test
    public void parseLineAsciiStringCustomCode() {
        HttpResponseStatus customStatus = parseLine(new AsciiString("612"));
        assertEquals(612, customStatus.code());
    }

    @Test
    public void parseLineAsciiStringCustomCodeAndPhrase() {
        HttpResponseStatus customStatus = parseLine(new AsciiString("612 FOO"));
        assertEquals(612, customStatus.code());
        assertEquals("FOO", customStatus.reasonPhrase());
    }

    @Test
    public void parseLineAsciiStringMalformedCode() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                parseLine(new AsciiString("200a"));
            }
        });
    }

    @Test
    public void parseLineAsciiStringMalformedCodeWithPhrase() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                parseLine(new AsciiString("200a foo"));
            }
        });
    }

    @Test
    public void testHttpStatusClassValueOf() {
        // status scope: [100, 600).
        for (int code = 100; code < 600; code ++) {
            HttpStatusClass httpStatusClass = HttpStatusClass.valueOf(code);
            assertNotSame(HttpStatusClass.UNKNOWN, httpStatusClass);
            if (HttpStatusClass.INFORMATIONAL.contains(code)) {
                assertEquals(HttpStatusClass.INFORMATIONAL, httpStatusClass);
            } else if (HttpStatusClass.SUCCESS.contains(code)) {
                assertEquals(HttpStatusClass.SUCCESS, httpStatusClass);
            } else if (HttpStatusClass.REDIRECTION.contains(code)) {
                assertEquals(HttpStatusClass.REDIRECTION, httpStatusClass);
            } else if (HttpStatusClass.CLIENT_ERROR.contains(code)) {
                assertEquals(HttpStatusClass.CLIENT_ERROR, httpStatusClass);
            } else if (HttpStatusClass.SERVER_ERROR.contains(code)) {
                assertEquals(HttpStatusClass.SERVER_ERROR, httpStatusClass);
            } else {
                fail("At least one of the if-branches above must be true");
            }
        }
        // status scope: [Integer.MIN_VALUE, 100).
        for (int code = Integer.MIN_VALUE; code < 100; code ++) {
            HttpStatusClass httpStatusClass = HttpStatusClass.valueOf(code);
            assertEquals(HttpStatusClass.UNKNOWN, httpStatusClass);
        }
        // status scope: [600, Integer.MAX_VALUE].
        for (int code = 600; code > 0; code ++) {
            HttpStatusClass httpStatusClass = HttpStatusClass.valueOf(code);
            assertEquals(HttpStatusClass.UNKNOWN, httpStatusClass);
        }
    }
}
