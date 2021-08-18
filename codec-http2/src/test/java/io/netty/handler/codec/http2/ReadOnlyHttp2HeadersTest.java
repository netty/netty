/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static io.netty.handler.codec.http2.DefaultHttp2HeadersTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReadOnlyHttp2HeadersTest {
    @Test
    public void notKeyValuePairThrows() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                ReadOnlyHttp2Headers.trailers(false, new AsciiString[]{ null });
            }
        });
    }

    @Test
    public void nullTrailersNotAllowed() {
        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() {
                ReadOnlyHttp2Headers.trailers(false, (AsciiString[]) null);
            }
        });
    }

    @Test
    public void nullHeaderNameNotChecked() {
        ReadOnlyHttp2Headers.trailers(false, null, null);
    }

    @Test
    public void nullHeaderNameValidated() {
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() {
                ReadOnlyHttp2Headers.trailers(true, null, new AsciiString("foo"));
            }
        });
    }

    @Test
    public void pseudoHeaderNotAllowedAfterNonPseudoHeaders() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                ReadOnlyHttp2Headers.trailers(true, new AsciiString(":name"), new AsciiString("foo"),
                        new AsciiString("othername"), new AsciiString("goo"),
                        new AsciiString(":pseudo"), new AsciiString("val"));
            }
        });
    }

    @Test
    public void nullValuesAreNotAllowed() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                ReadOnlyHttp2Headers.trailers(true, new AsciiString("foo"), null);
            }
        });
    }

    @Test
    public void emptyHeaderNameAllowed() {
        ReadOnlyHttp2Headers.trailers(false, AsciiString.EMPTY_STRING, new AsciiString("foo"));
    }

    @Test
    public void testPseudoHeadersMustComeFirstWhenIteratingServer() {
        Http2Headers headers = newServerHeaders();
        verifyPseudoHeadersFirst(headers);
    }

    @Test
    public void testPseudoHeadersMustComeFirstWhenIteratingClient() {
        Http2Headers headers = newClientHeaders();
        verifyPseudoHeadersFirst(headers);
    }

    @Test
    public void testIteratorReadOnlyClient() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                testIteratorReadOnly(newClientHeaders());
            }
        });
    }

    @Test
    public void testIteratorReadOnlyServer() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                testIteratorReadOnly(newServerHeaders());
            }
        });
    }

    @Test
    public void testIteratorReadOnlyTrailers() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                testIteratorReadOnly(newTrailers());
            }
        });
    }

    @Test
    public void testIteratorEntryReadOnlyClient() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                testIteratorEntryReadOnly(newClientHeaders());
            }
        });
    }

    @Test
    public void testIteratorEntryReadOnlyServer() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                testIteratorEntryReadOnly(newServerHeaders());
            }
        });
    }

    @Test
    public void testIteratorEntryReadOnlyTrailers() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                testIteratorEntryReadOnly(newTrailers());
            }
        });
    }

    @Test
    public void testSize() {
        Http2Headers headers = newTrailers();
        assertEquals(otherHeaders().length / 2, headers.size());
    }

    @Test
    public void testIsNotEmpty() {
        Http2Headers headers = newTrailers();
        assertFalse(headers.isEmpty());
    }

    @Test
    public void testIsEmpty() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false);
        assertTrue(headers.isEmpty());
    }

    @Test
    public void testContainsName() {
        Http2Headers headers = newClientHeaders();
        assertTrue(headers.contains("Name1"));
        assertTrue(headers.contains(Http2Headers.PseudoHeaderName.PATH.value()));
        assertFalse(headers.contains(Http2Headers.PseudoHeaderName.STATUS.value()));
        assertFalse(headers.contains("a missing header"));
    }

    @Test
    public void testContainsNameAndValue() {
        Http2Headers headers = newClientHeaders();
        assertTrue(headers.contains("Name1", "value1"));
        assertFalse(headers.contains("Name1", "Value1"));
        assertTrue(headers.contains("name2", "Value2", true));
        assertFalse(headers.contains("name2", "Value2", false));
        assertTrue(headers.contains(Http2Headers.PseudoHeaderName.PATH.value(), "/foo"));
        assertFalse(headers.contains(Http2Headers.PseudoHeaderName.STATUS.value(), "200"));
        assertFalse(headers.contains("a missing header", "a missing value"));
    }

    @Test
    public void testGet() {
        Http2Headers headers = newClientHeaders();
        assertTrue(AsciiString.contentEqualsIgnoreCase("value1", headers.get("Name1")));
        assertTrue(AsciiString.contentEqualsIgnoreCase("/foo",
                   headers.get(Http2Headers.PseudoHeaderName.PATH.value())));
        assertNull(headers.get(Http2Headers.PseudoHeaderName.STATUS.value()));
        assertNull(headers.get("a missing header"));
    }

    @Test
    public void testClientOtherValueIterator() {
        testValueIteratorSingleValue(newClientHeaders(), "name2", "value2");
    }

    @Test
    public void testClientPsuedoValueIterator() {
        testValueIteratorSingleValue(newClientHeaders(), ":path", "/foo");
    }

    @Test
    public void testServerPsuedoValueIterator() {
        testValueIteratorSingleValue(newServerHeaders(), ":status", "200");
    }

    @Test
    public void testEmptyValueIterator() {
        Http2Headers headers = newServerHeaders();
        final Iterator<CharSequence> itr = headers.valueIterator("foo");
        assertFalse(itr.hasNext());
        assertThrows(NoSuchElementException.class, new Executable() {
            @Override
            public void execute() {
                itr.next();
            }
        });
    }

    @Test
    public void testIteratorMultipleValues() {
        Http2Headers headers = ReadOnlyHttp2Headers.serverHeaders(false, new AsciiString("200"),
                new AsciiString("name2"), new AsciiString("value1"),
                new AsciiString("name1"), new AsciiString("value2"),
                new AsciiString("name2"), new AsciiString("value3"));
        Iterator<CharSequence> itr = headers.valueIterator("name2");
        assertTrue(itr.hasNext());
        assertTrue(AsciiString.contentEqualsIgnoreCase("value1", itr.next()));
        assertTrue(itr.hasNext());
        assertTrue(AsciiString.contentEqualsIgnoreCase("value3", itr.next()));
        assertFalse(itr.hasNext());
    }

    private static void testValueIteratorSingleValue(Http2Headers headers, CharSequence name, CharSequence value) {
        Iterator<CharSequence> itr = headers.valueIterator(name);
        assertTrue(itr.hasNext());
        assertTrue(AsciiString.contentEqualsIgnoreCase(value, itr.next()));
        assertFalse(itr.hasNext());
    }

    private static void testIteratorReadOnly(Http2Headers headers) {
        Iterator<Map.Entry<CharSequence, CharSequence>> itr = headers.iterator();
        assertTrue(itr.hasNext());
        itr.remove();
    }

    private static void testIteratorEntryReadOnly(Http2Headers headers) {
        Iterator<Map.Entry<CharSequence, CharSequence>> itr = headers.iterator();
        assertTrue(itr.hasNext());
        itr.next().setValue("foo");
    }

    private static ReadOnlyHttp2Headers newServerHeaders() {
        return ReadOnlyHttp2Headers.serverHeaders(false, new AsciiString("200"), otherHeaders());
    }

    private static ReadOnlyHttp2Headers newClientHeaders() {
        return ReadOnlyHttp2Headers.clientHeaders(false, new AsciiString("meth"), new AsciiString("/foo"),
                new AsciiString("schemer"), new AsciiString("respect_my_authority"), otherHeaders());
    }

    private static ReadOnlyHttp2Headers newTrailers() {
        return ReadOnlyHttp2Headers.trailers(false, otherHeaders());
    }

    private static AsciiString[] otherHeaders() {
        return new AsciiString[] {
                new AsciiString("name1"), new AsciiString("value1"),
                new AsciiString("name2"), new AsciiString("value2"),
                new AsciiString("name3"), new AsciiString("value3")
        };
    }
}
