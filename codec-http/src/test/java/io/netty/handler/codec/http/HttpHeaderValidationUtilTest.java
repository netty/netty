/*
 * Copyright 2022 The Netty Project
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
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static io.netty.handler.codec.http.HttpHeaderValidationUtil.validateToken;
import static io.netty.handler.codec.http.HttpHeaderValidationUtil.validateValidHeaderValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Execution(ExecutionMode.CONCURRENT) // We have a couple of fairly slow tests here. Better to run them in parallel.
public class HttpHeaderValidationUtilTest {
    @SuppressWarnings("deprecation") // We need to check for deprecated headers as well.
    public static List<Arguments> connectionRelatedHeaders() {
        List<Arguments> list = new ArrayList<Arguments>();

        list.add(header(false, HttpHeaderNames.ACCEPT));
        list.add(header(false, HttpHeaderNames.ACCEPT_CHARSET));
        list.add(header(false, HttpHeaderNames.ACCEPT_ENCODING));
        list.add(header(false, HttpHeaderNames.ACCEPT_LANGUAGE));
        list.add(header(false, HttpHeaderNames.ACCEPT_RANGES));
        list.add(header(false, HttpHeaderNames.ACCEPT_PATCH));
        list.add(header(false, HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        list.add(header(false, HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS));
        list.add(header(false, HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS));
        list.add(header(false, HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN));
        list.add(header(false, HttpHeaderNames.ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK));
        list.add(header(false, HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS));
        list.add(header(false, HttpHeaderNames.ACCESS_CONTROL_MAX_AGE));
        list.add(header(false, HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS));
        list.add(header(false, HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD));
        list.add(header(false, HttpHeaderNames.ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK));
        list.add(header(false, HttpHeaderNames.AGE));
        list.add(header(false, HttpHeaderNames.ALLOW));
        list.add(header(false, HttpHeaderNames.AUTHORIZATION));
        list.add(header(false, HttpHeaderNames.CACHE_CONTROL));
        list.add(header(true, HttpHeaderNames.CONNECTION));
        list.add(header(false, HttpHeaderNames.CONTENT_BASE));
        list.add(header(false, HttpHeaderNames.CONTENT_ENCODING));
        list.add(header(false, HttpHeaderNames.CONTENT_LANGUAGE));
        list.add(header(false, HttpHeaderNames.CONTENT_LENGTH));
        list.add(header(false, HttpHeaderNames.CONTENT_LOCATION));
        list.add(header(false, HttpHeaderNames.CONTENT_TRANSFER_ENCODING));
        list.add(header(false, HttpHeaderNames.CONTENT_DISPOSITION));
        list.add(header(false, HttpHeaderNames.CONTENT_MD5));
        list.add(header(false, HttpHeaderNames.CONTENT_RANGE));
        list.add(header(false, HttpHeaderNames.CONTENT_SECURITY_POLICY));
        list.add(header(false, HttpHeaderNames.CONTENT_TYPE));
        list.add(header(false, HttpHeaderNames.COOKIE));
        list.add(header(false, HttpHeaderNames.DATE));
        list.add(header(false, HttpHeaderNames.DNT));
        list.add(header(false, HttpHeaderNames.ETAG));
        list.add(header(false, HttpHeaderNames.EXPECT));
        list.add(header(false, HttpHeaderNames.EXPIRES));
        list.add(header(false, HttpHeaderNames.FROM));
        list.add(header(false, HttpHeaderNames.HOST));
        list.add(header(false, HttpHeaderNames.IF_MATCH));
        list.add(header(false, HttpHeaderNames.IF_MODIFIED_SINCE));
        list.add(header(false, HttpHeaderNames.IF_NONE_MATCH));
        list.add(header(false, HttpHeaderNames.IF_RANGE));
        list.add(header(false, HttpHeaderNames.IF_UNMODIFIED_SINCE));
        list.add(header(true, HttpHeaderNames.KEEP_ALIVE));
        list.add(header(false, HttpHeaderNames.LAST_MODIFIED));
        list.add(header(false, HttpHeaderNames.LOCATION));
        list.add(header(false, HttpHeaderNames.MAX_FORWARDS));
        list.add(header(false, HttpHeaderNames.ORIGIN));
        list.add(header(false, HttpHeaderNames.PRAGMA));
        list.add(header(false, HttpHeaderNames.PROXY_AUTHENTICATE));
        list.add(header(false, HttpHeaderNames.PROXY_AUTHORIZATION));
        list.add(header(true, HttpHeaderNames.PROXY_CONNECTION));
        list.add(header(false, HttpHeaderNames.RANGE));
        list.add(header(false, HttpHeaderNames.REFERER));
        list.add(header(false, HttpHeaderNames.RETRY_AFTER));
        list.add(header(false, HttpHeaderNames.SEC_WEBSOCKET_KEY1));
        list.add(header(false, HttpHeaderNames.SEC_WEBSOCKET_KEY2));
        list.add(header(false, HttpHeaderNames.SEC_WEBSOCKET_LOCATION));
        list.add(header(false, HttpHeaderNames.SEC_WEBSOCKET_ORIGIN));
        list.add(header(false, HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL));
        list.add(header(false, HttpHeaderNames.SEC_WEBSOCKET_VERSION));
        list.add(header(false, HttpHeaderNames.SEC_WEBSOCKET_KEY));
        list.add(header(false, HttpHeaderNames.SEC_WEBSOCKET_ACCEPT));
        list.add(header(false, HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));
        list.add(header(false, HttpHeaderNames.SERVER));
        list.add(header(false, HttpHeaderNames.SET_COOKIE));
        list.add(header(false, HttpHeaderNames.SET_COOKIE2));
        list.add(header(true, HttpHeaderNames.TE));
        list.add(header(false, HttpHeaderNames.TRAILER));
        list.add(header(true, HttpHeaderNames.TRANSFER_ENCODING));
        list.add(header(true, HttpHeaderNames.UPGRADE));
        list.add(header(false, HttpHeaderNames.UPGRADE_INSECURE_REQUESTS));
        list.add(header(false, HttpHeaderNames.USER_AGENT));
        list.add(header(false, HttpHeaderNames.VARY));
        list.add(header(false, HttpHeaderNames.VIA));
        list.add(header(false, HttpHeaderNames.WARNING));
        list.add(header(false, HttpHeaderNames.WEBSOCKET_LOCATION));
        list.add(header(false, HttpHeaderNames.WEBSOCKET_ORIGIN));
        list.add(header(false, HttpHeaderNames.WEBSOCKET_PROTOCOL));
        list.add(header(false, HttpHeaderNames.WWW_AUTHENTICATE));
        list.add(header(false, HttpHeaderNames.X_FRAME_OPTIONS));
        list.add(header(false, HttpHeaderNames.X_REQUESTED_WITH));

        return list;
    }

    private static Arguments header(final boolean isConnectionRelated, final AsciiString headerName) {
        return new Arguments() {
            @Override
            public Object[] get() {
                return new Object[]{headerName, isConnectionRelated};
            }
        };
    }

    @ParameterizedTest
    @MethodSource("connectionRelatedHeaders")
    void mustIdentifyConnectionRelatedHeadersAsciiString(AsciiString headerName, boolean isConnectionRelated) {
        assertEquals(isConnectionRelated, HttpHeaderValidationUtil.isConnectionHeader(headerName, false));
    }

    @ParameterizedTest
    @MethodSource("connectionRelatedHeaders")
    void mustIdentifyConnectionRelatedHeadersString(AsciiString headerName, boolean isConnectionRelated) {
        assertEquals(isConnectionRelated, HttpHeaderValidationUtil.isConnectionHeader(headerName.toString(), false));
    }

    @Test
    void teHeaderIsNotConnectionRelatedWhenIgnoredAsciiString() {
        assertFalse(HttpHeaderValidationUtil.isConnectionHeader(HttpHeaderNames.TE, true));
    }

    @Test
    void teHeaderIsNotConnectionRelatedWhenIgnoredString() {
        assertFalse(HttpHeaderValidationUtil.isConnectionHeader(HttpHeaderNames.TE.toString(), true));
    }

    public static List<Arguments> teIsTrailersTruthTable() {
        List<Arguments> list = new ArrayList<Arguments>();

        list.add(teIsTrailter(HttpHeaderNames.TE, HttpHeaderValues.TRAILERS, false));
        list.add(teIsTrailter(HttpHeaderNames.TE, HttpHeaderValues.CHUNKED, true));
        list.add(teIsTrailter(HttpHeaderNames.COOKIE, HttpHeaderValues.CHUNKED, false));
        list.add(teIsTrailter(HttpHeaderNames.COOKIE, HttpHeaderValues.TRAILERS, false));
        list.add(teIsTrailter(HttpHeaderNames.TRAILER, HttpHeaderValues.TRAILERS, false));
        list.add(teIsTrailter(HttpHeaderNames.TRAILER, HttpHeaderValues.CHUNKED, false));

        return list;
    }

    private static Arguments teIsTrailter(
            final AsciiString headerName, final AsciiString headerValue, final boolean result) {
        return new Arguments() {
            @Override
            public Object[] get() {
                return new Object[]{headerName, headerValue, result};
            }
        };
    }

    @ParameterizedTest
    @MethodSource("teIsTrailersTruthTable")
    void whenTeIsNotTrailerOrNotWithNameAndValueAsciiString(
            AsciiString headerName, AsciiString headerValue, boolean result) {
        assertEquals(result, HttpHeaderValidationUtil.isTeNotTrailers(headerName, headerValue));
    }

    @ParameterizedTest
    @MethodSource("teIsTrailersTruthTable")
    void whenTeIsNotTrailerOrNotSWithNameAndValueString(
            AsciiString headerName, AsciiString headerValue, boolean result) {
        assertEquals(result, HttpHeaderValidationUtil.isTeNotTrailers(headerName.toString(), headerValue.toString()));
    }

    @ParameterizedTest
    @MethodSource("teIsTrailersTruthTable")
    void whenTeIsNotTrailerOrNotSWithNameAsciiStringAndValueString(
            AsciiString headerName, AsciiString headerValue, boolean result) {
        assertEquals(result, HttpHeaderValidationUtil.isTeNotTrailers(headerName, headerValue.toString()));
    }

    @ParameterizedTest
    @MethodSource("teIsTrailersTruthTable")
    void whenTeIsNotTrailerOrNotSWithNametringAndValueAsciiString(
            AsciiString headerName, AsciiString headerValue, boolean result) {
        assertEquals(result, HttpHeaderValidationUtil.isTeNotTrailers(headerName.toString(), headerValue));
    }

    public static List<AsciiString> illegalFirstChar() {
        List<AsciiString> list = new ArrayList<AsciiString>();

        for (byte i = 0; i < 0x21; i++) {
            list.add(new AsciiString(new byte[]{i, 'a'}));
        }
        list.add(new AsciiString(new byte[]{0x7F, 'a'}));

        return list;
    }

    @ParameterizedTest
    @MethodSource("illegalFirstChar")
    void decodingInvalidHeaderValuesMustFailIfFirstCharIsIllegalAsciiString(AsciiString value) {
        assertEquals(0, validateValidHeaderValue(value));
    }

    @ParameterizedTest
    @MethodSource("illegalFirstChar")
    void decodingInvalidHeaderValuesMustFailIfFirstCharIsIllegalCharSequence(AsciiString value) {
        assertEquals(0, validateValidHeaderValue(asCharSequence(value)));
    }

    public static List<AsciiString> legalFirstChar() {
        List<AsciiString> list = new ArrayList<AsciiString>();

        for (int i = 0x21; i <= 0xFF; i++) {
            if (i == 0x7F) {
                continue;
            }
            list.add(new AsciiString(new byte[]{(byte) i, 'a'}));
        }

        return list;
    }

    @ParameterizedTest
    @MethodSource("legalFirstChar")
    void allOtherCharsAreLegalFirstCharsAsciiString(AsciiString value) {
        assertEquals(-1, validateValidHeaderValue(value));
    }

    @ParameterizedTest
    @MethodSource("legalFirstChar")
    void allOtherCharsAreLegalFirstCharsCharSequence(AsciiString value) {
        assertEquals(-1, validateValidHeaderValue(value));
    }

    public static List<AsciiString> illegalNotFirstChar() {
        ArrayList<AsciiString> list = new ArrayList<AsciiString>();

        for (byte i = 0; i < 0x21; i++) {
            if (i == ' ' || i == '\t') {
                continue; // Space and horizontal tab are only illegal as first chars.
            }
            list.add(new AsciiString(new byte[]{'a', i}));
        }
        list.add(new AsciiString(new byte[]{'a', 0x7F}));

        return list;
    }

    @ParameterizedTest
    @MethodSource("illegalNotFirstChar")
    void decodingInvalidHeaderValuesMustFailIfNotFirstCharIsIllegalAsciiString(AsciiString value) {
        assertEquals(1, validateValidHeaderValue(value));
    }

    @ParameterizedTest
    @MethodSource("illegalNotFirstChar")
    void decodingInvalidHeaderValuesMustFailIfNotFirstCharIsIllegalCharSequence(AsciiString value) {
        assertEquals(1, validateValidHeaderValue(asCharSequence(value)));
    }

    public static List<AsciiString> legalNotFirstChar() {
        List<AsciiString> list = new ArrayList<AsciiString>();

        for (int i = 0; i < 0xFF; i++) {
            if (i == 0x7F || i < 0x21 && (i != ' ' || i != '\t')) {
                continue;
            }
            list.add(new AsciiString(new byte[] {'a', (byte) i}));
        }

        return list;
    }

    @ParameterizedTest
    @MethodSource("legalNotFirstChar")
    void allOtherCharsArgLegalNotFirstCharsAsciiString(AsciiString value) {
        assertEquals(-1, validateValidHeaderValue(value));
    }

    @ParameterizedTest
    @MethodSource("legalNotFirstChar")
    void allOtherCharsArgLegalNotFirstCharsCharSequence(AsciiString value) {
        assertEquals(-1, validateValidHeaderValue(asCharSequence(value)));
    }

    @Test
    void emptyValuesHaveNoIllegalCharsAsciiString() {
        assertEquals(-1, validateValidHeaderValue(AsciiString.EMPTY_STRING));
    }

    @Test
    void emptyValuesHaveNoIllegalCharsCharSequence() {
        assertEquals(-1, validateValidHeaderValue(asCharSequence(AsciiString.EMPTY_STRING)));
    }

    @Test
    void headerValuesCannotEndWithNewlinesAsciiString() {
        assertEquals(1, validateValidHeaderValue(AsciiString.of("a\n")));
        assertEquals(1, validateValidHeaderValue(AsciiString.of("a\r")));
    }

    @Test
    void headerValuesCannotEndWithNewlinesCharSequence() {
        assertEquals(1, validateValidHeaderValue("a\n"));
        assertEquals(1, validateValidHeaderValue("a\r"));
    }

    /**
     * This method returns a {@link CharSequence} instance that has the same contents as the given {@link AsciiString},
     * but which is, critically, <em>not</em> itself an {@link AsciiString}.
     * <p>
     * Some methods specialise on {@link AsciiString}, while having a {@link CharSequence} based fallback.
     * <p>
     * This method exist to test those fallback methods.
     *
     * @param value The {@link AsciiString} instance to wrap.
     * @return A new {@link CharSequence} instance which backed by the given {@link AsciiString},
     * but which is itself not an {@link AsciiString}.
     */
    private static CharSequence asCharSequence(final AsciiString value) {
        return new CharSequence() {
            @Override
            public int length() {
                return value.length();
            }

            @Override
            public char charAt(int index) {
                return value.charAt(index);
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                return asCharSequence(value.subSequence(start, end));
            }
        };
    }

    private static final IllegalArgumentException VALIDATION_EXCEPTION = new IllegalArgumentException() {
        private static final long serialVersionUID = -8857428534361331089L;

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    };

    @DisabledForJreRange(max = JRE.JAVA_17) // This test is much too slow on older Java versions.
    @Test
    void headerValueValidationMustRejectAllValuesRejectedByOldAlgorithm() {
        byte[] array = new byte[4];
        final ByteBuffer buffer = ByteBuffer.wrap(array);
        final AsciiString asciiString = new AsciiString(buffer, false);
        CharSequence charSequence = asCharSequence(asciiString);
        int i = Integer.MIN_VALUE;
        Supplier<String> failureMessageSupplier = new Supplier<String>() {
            @Override
            public String get() {
                return "validation mismatch on string '" + asciiString + "', iteration " + buffer.getInt(0);
            }
        };

        do {
            buffer.putInt(0, i);
            try {
                oldHeaderValueValidationAlgorithm(asciiString);
            } catch (IllegalArgumentException ignore) {
                assertNotEquals(-1, validateValidHeaderValue(asciiString), failureMessageSupplier);
                assertNotEquals(-1, validateValidHeaderValue(charSequence), failureMessageSupplier);
            }
            i++;
        } while (i != Integer.MIN_VALUE);
    }

    private static void oldHeaderValueValidationAlgorithm(CharSequence seq) {
        int state = 0;
        // Start looping through each of the character
        for (int index = 0; index < seq.length(); index++) {
            state = oldValidationAlgorithmValidateValueChar(state, seq.charAt(index));
        }

        if (state != 0) {
            throw VALIDATION_EXCEPTION;
        }
    }

    private static int oldValidationAlgorithmValidateValueChar(int state, char character) {
        /*
         * State:
         * 0: Previous character was neither CR nor LF
         * 1: The previous character was CR
         * 2: The previous character was LF
         */
        if ((character & ~15) == 0) {
            // Check the absolutely prohibited characters.
            switch (character) {
                case 0x0: // NULL
                    throw VALIDATION_EXCEPTION;
                case 0x0b: // Vertical tab
                    throw VALIDATION_EXCEPTION;
                case '\f':
                    throw VALIDATION_EXCEPTION;
                default:
                    break;
            }
        }

        // Check the CRLF (HT | SP) pattern
        switch (state) {
            case 0:
                switch (character) {
                    case '\r':
                        return 1;
                    case '\n':
                        return 2;
                    default:
                        break;
                }
                break;
            case 1:
                if (character == '\n') {
                    return 2;
                }
                throw VALIDATION_EXCEPTION;
            case 2:
                switch (character) {
                    case '\t':
                    case ' ':
                        return 0;
                    default:
                        throw VALIDATION_EXCEPTION;
                }
            default:
                break;
        }
        return state;
    }

    @DisabledForJreRange(max = JRE.JAVA_17) // This test is much too slow on older Java versions.
    @Test
    void headerNameValidationMustRejectAllNamesRejectedByOldAlgorithm() throws Exception {
        byte[] array = new byte[4];
        final ByteBuffer buffer = ByteBuffer.wrap(array);
        final AsciiString asciiString = new AsciiString(buffer, false);
        CharSequence charSequence = asCharSequence(asciiString);
        int i = Integer.MIN_VALUE;
        Supplier<String> failureMessageSupplier = new Supplier<String>() {
            @Override
            public String get() {
                return "validation mismatch on string '" + asciiString + "', iteration " + buffer.getInt(0);
            }
        };

        do {
            buffer.putInt(0, i);
            try {
                oldHeaderNameValidationAlgorithmAsciiString(asciiString);
            } catch (IllegalArgumentException ignore) {
                assertNotEquals(-1, validateToken(asciiString), failureMessageSupplier);
                assertNotEquals(-1, validateToken(charSequence), failureMessageSupplier);
            }
            i++;
        } while (i != Integer.MIN_VALUE);
    }

    private static void oldHeaderNameValidationAlgorithmAsciiString(AsciiString name) throws Exception {
        byte[] array = name.array();
        for (int i = name.arrayOffset(), len = name.arrayOffset() + name.length(); i < len; i++) {
            validateHeaderNameElement(array[i]);
        }
    }

    private static void validateHeaderNameElement(byte value) {
        switch (value) {
            case 0x1c:
            case 0x1d:
            case 0x1e:
            case 0x1f:
            case 0x00:
            case '\t':
            case '\n':
            case 0x0b:
            case '\f':
            case '\r':
            case ' ':
            case ',':
            case ':':
            case ';':
            case '=':
                throw VALIDATION_EXCEPTION;
            default:
                // Check to see if the character is not an ASCII character, or invalid
                if (value < 0) {
                    throw VALIDATION_EXCEPTION;
                }
        }
    }

    public static List<Character> validTokenChars() {
        List<Character> list = new ArrayList<Character>();
        for (char c = '0'; c <= '9'; c++) {
            list.add(c);
        }
        for (char c = 'a'; c <= 'z'; c++) {
            list.add(c);
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            list.add(c);
        }

        // Unreserved characters:
        list.add('-');
        list.add('.');
        list.add('_');
        list.add('~');

        // Token special characters:
        list.add('!');
        list.add('#');
        list.add('$');
        list.add('%');
        list.add('&');
        list.add('\'');
        list.add('*');
        list.add('+');
        list.add('^');
        list.add('`');
        list.add('|');

        return list;
    }

    @ParameterizedTest
    @MethodSource("validTokenChars")
    void allTokenCharsAreValidFirstCharHeaderName(char tokenChar) {
        AsciiString asciiString = new AsciiString(new byte[] {(byte) tokenChar, 'a'});
        CharSequence charSequence = asCharSequence(asciiString);
        String string = tokenChar + "a";

        assertEquals(-1, validateToken(asciiString));
        assertEquals(-1, validateToken(charSequence));
        assertEquals(-1, validateToken(string));
    }

    @ParameterizedTest
    @MethodSource("validTokenChars")
    void allTokenCharsAreValidSecondCharHeaderName(char tokenChar) {
        AsciiString asciiString = new AsciiString(new byte[] {'a', (byte) tokenChar});
        CharSequence charSequence = asCharSequence(asciiString);
        String string = "a" + tokenChar;

        assertEquals(-1, validateToken(asciiString));
        assertEquals(-1, validateToken(charSequence));
        assertEquals(-1, validateToken(string));
    }
}
