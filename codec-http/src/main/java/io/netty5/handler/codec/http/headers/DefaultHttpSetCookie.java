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

import io.netty5.util.AsciiString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.netty5.handler.codec.http.headers.HeaderUtils.validateCookieNameAndValue;
import static io.netty5.util.AsciiString.contentEquals;
import static io.netty5.util.AsciiString.contentEqualsIgnoreCase;
import static java.lang.Integer.toHexString;
import static java.lang.Long.parseLong;

/**
 * Default implementation of {@link HttpSetCookie}.
 */
public final class DefaultHttpSetCookie implements HttpSetCookie {
    private static final String ENCODED_LABEL_DOMAIN = "; domain=";
    private static final String ENCODED_LABEL_PATH = "; path=";
    private static final String ENCODED_LABEL_EXPIRES = "; expires=";
    private static final String ENCODED_LABEL_MAX_AGE = "; max-age=";
    private static final String ENCODED_LABEL_HTTP_ONLY = "; httponly";
    private static final String ENCODED_LABEL_SECURE = "; secure";
    private static final String ENCODED_LABEL_SAMESITE = "; samesite=";
    private static final String ENCODED_LABEL_PARTITIONED = "; Partitioned";

    private static ParseState parseStateOf(CharSequence fieldName) {
        // Try a binary search based on length. We can read length without bounds checks.
        int len = fieldName.length();
        if (len >= 4 && len <= 8) {
            if (len < 7) {
                if (len == 4) {
                    if (contentEqualsIgnoreCase("path", fieldName)) {
                        return ParseState.ParsingPath;
                    }
                } else if (len == 6) {
                    if (contentEqualsIgnoreCase("domain", fieldName)) {
                        return ParseState.ParsingDomain;
                    }
                }
            } else {
                if (len == 7) {
                    if (contentEqualsIgnoreCase("expires", fieldName)) {
                        return ParseState.ParsingExpires;
                    }
                    if (contentEqualsIgnoreCase("max-age", fieldName)) {
                        return ParseState.ParsingMaxAge;
                    }
                } else {
                    if (contentEqualsIgnoreCase("samesite", fieldName)) {
                        return ParseState.ParsingSameSite;
                    }
                }
            }
        }
        return ParseState.Unknown;
    }

    private final CharSequence name;
    private final CharSequence value;
    @Nullable
    private final CharSequence path;
    @Nullable
    private final CharSequence domain;
    @Nullable
    private final CharSequence expires;
    @Nullable
    private final Long maxAge;
    @Nullable
    private final SameSite sameSite;
    private final boolean wrapped;
    private final boolean secure;
    private final boolean httpOnly;
    private final boolean partitioned;

    /**
     * Create a new not wrapped, not secure and not HTTP-only {@link HttpSetCookie} instance, with no path, domain,
     * expire date and maximum age.
     *
     * @param name the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>.
     * @param value the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-value</a>.
     */
    public DefaultHttpSetCookie(final CharSequence name, final CharSequence value) {
        this(name, value, false, false, false);
    }

    /**
     * Create a new {@link HttpSetCookie} instance, with no path, domain, expire date and maximum age.
     *
     * @param name the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>.
     * @param value the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-value</a>.
     * @param wrapped {@code true} if the value should be wrapped in DQUOTE as described in
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-value</a>.
     * @param secure the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">secure-av</a>.
     * @param httpOnly the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">httponly-av</a> (see
     * <a href="https://www.owasp.org/index.php/HTTPOnly">HTTP-only</a>).
     */
    public DefaultHttpSetCookie(final CharSequence name, final CharSequence value,
                                final boolean wrapped, final boolean secure, final boolean httpOnly) {
        this(name, value, null, null, null, null, null, wrapped, secure, httpOnly, false);
    }

    /**
     * Creates a new {@link HttpSetCookie} instance.
     *
     * @param name the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>.
     * @param value the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-value</a>.
     * @param path the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">path-value</a>.
     * @param domain the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">domain-value</a>.
     * @param expires the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">expires-av</a>.
     * Represented as an RFC-1123 date defined in
     * <a href="https://tools.ietf.org/html/rfc2616#section-3.3.1">RFC-2616, Section 3.3.1</a>.
     * @param maxAge the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">max-age-av</a>.
     * @param wrapped {@code true} if the value should be wrapped in DQUOTE as described in
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-value</a>.
     * @param secure the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">secure-av</a>.
     * @param httpOnly the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">httponly-av</a> (see
     * <a href="https://www.owasp.org/index.php/HTTPOnly">HTTP-only</a>).
     * @param sameSite the
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-05#section-5.3.7">SameSite attribute</a>.
     */
    public DefaultHttpSetCookie(final CharSequence name, final CharSequence value, @Nullable final CharSequence path,
                                @Nullable final CharSequence domain, @Nullable final CharSequence expires,
                                @Nullable final Long maxAge, @Nullable final SameSite sameSite, final boolean wrapped,
                                final boolean secure, final boolean httpOnly) {
        this(name, value, path, domain, expires, maxAge, sameSite, wrapped, secure, httpOnly, false);
    }

    /**
     * Creates a new {@link HttpSetCookie} instance.
     *
     * @param name the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>.
     * @param value the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-value</a>.
     * @param path the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">path-value</a>.
     * @param domain the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">domain-value</a>.
     * @param expires the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">expires-av</a>.
     * Represented as an RFC-1123 date defined in
     * <a href="https://tools.ietf.org/html/rfc2616#section-3.3.1">RFC-2616, Section 3.3.1</a>.
     * @param maxAge the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">max-age-av</a>.
     * @param wrapped {@code true} if the value should be wrapped in DQUOTE as described in
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-value</a>.
     * @param secure the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">secure-av</a>.
     * @param httpOnly the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">httponly-av</a> (see
     * <a href="https://www.owasp.org/index.php/HTTPOnly">HTTP-only</a>).
     * @param sameSite the
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-05#section-5.3.7">SameSite attribute</a>.
     * @param partitioned the {@code Partitioned} attribute.
     * (see <a href="https://developers.google.com/privacy-sandbox/3pcd/chips">Partitioned attribute</a>)
     */
    public DefaultHttpSetCookie(final CharSequence name, final CharSequence value, @Nullable final CharSequence path,
                                @Nullable final CharSequence domain, @Nullable final CharSequence expires,
                                @Nullable final Long maxAge, @Nullable final SameSite sameSite, final boolean wrapped,
                                final boolean secure, final boolean httpOnly, final boolean partitioned) {
        validateCookieNameAndValue(name, value);
        this.name = name;
        this.value = value;
        this.path = path;
        this.domain = domain;
        this.expires = expires;
        this.maxAge = maxAge;
        this.sameSite = sameSite;
        this.wrapped = wrapped;
        this.secure = secure;
        this.httpOnly = httpOnly;
        this.partitioned = partitioned;
    }

    static HttpSetCookie parseSetCookie(final CharSequence setCookieString, boolean validateContent,
                                        @Nullable CharSequence name, int i) {
        CharSequence value = null;
        CharSequence path = null;
        CharSequence domain = null;
        CharSequence expires = null;
        Long maxAge = null;
        SameSite sameSite = null;
        boolean isWrapped = false;
        boolean isSecure = false;
        boolean isHttpOnly = false;
        boolean isPartitioned = false;
        int begin;
        ParseState parseState;
        if (name != null) {
            parseState = ParseState.ParsingValue;
            begin = i;
        } else {
            parseState = ParseState.Unknown;
            begin = 0;
        }

        int length = setCookieString.length();
        while (i < length) {
            final char c = setCookieString.charAt(i);
            switch (c) {
                case '=':
                    if (name == null) {
                        if (i <= begin) {
                            throw new IllegalArgumentException("cookie name cannot be null or empty");
                        }
                        name = setCookieString.subSequence(begin, i);
                        if (validateContent) {
                            int index = HttpHeaderValidationUtil.validateToken(name);
                            if (index != -1) {
                                throw new HeaderValidationException(
                                        "a cookie name can only contain \"token\" characters, " +
                                        "but found invalid character 0x" + toHexString(c) +
                                        " at index " + index + " of header '" + name + "'.");
                            }
                        }
                        parseState = ParseState.ParsingValue;
                    } else if (parseState == ParseState.Unknown) {
                        final CharSequence avName = setCookieString.subSequence(begin, i);
                        parseState = parseStateOf(avName);
                    } else if (parseState == ParseState.ParsingValue) {
                        // Cookie values can contain '='.
                        ++i;
                        break;
                    } else {
                        throw new IllegalArgumentException("set-cookie '" + name + "': unexpected = at index: " + i);
                    }
                    ++i;
                    begin = i;
                    break;
                case '"':
                    if (parseState == ParseState.ParsingValue) {
                        if (isWrapped) {
                            parseState = ParseState.Unknown;
                            value = setCookieString.subSequence(begin, i);
                            // Increment by 3 because we are skipping DQUOTE SEMI SP
                             // See https://www.rfc-editor.org/rfc/rfc6265#section-4.1.1
                             // Specifically, how set-cookie-string interacts with quoted cookie-value.
                             ++i;    // go to SEMI
                             if (i < length && setCookieString.charAt(i) != ';') {
                                 throw new IllegalArgumentException(
                                         "set-cookie '" + name + "': unexpected character 0x" +
                                                 toHexString(setCookieString.charAt(i)) + " at index " + i +
                                                 ", expected semicolon ';' after quoted value");
                             }
                             ++i;    // go to SP
                             if (validateContent && HeaderUtils.cookieParsingStrictRfc6265()) {
                                 if (i < length && setCookieString.charAt(i) != ' ') {
                                     throw new IllegalArgumentException("set-cookie '" + name +
                                             "': a space is required after ; in cookie attribute-value lists");
                                 }
                                 ++i;    // skip SP
                             } else {
                                 // When validation is disabled, we need to check if there's an SP to skip
                                 if (i < length && setCookieString.charAt(i) == ' ') {
                                     ++i;
                                 }
                             }
                        } else {
                            isWrapped = true;
                            ++i;
                        }
                        begin = i;
                        break;
                    }
                    if (value == null) {
                        throw new IllegalArgumentException(
                                "set-cookie '" + name + "': unexpected quote at index: " + i);
                    }
                    ++i;
                    break;
                case ';':
                    // end of value, or end of av-value
                    if (i + 1 == length && validateContent) {
                        throw new IllegalArgumentException("set-cookie '" + name + "': unexpected trailing ';'");
                    }
                    switch (parseState) {
                        case ParsingValue:
                            value = setCookieString.subSequence(begin, i);
                            break;
                        case ParsingPath:
                            path = setCookieString.subSequence(begin, i);
                            break;
                        case ParsingDomain:
                            domain = setCookieString.subSequence(begin, i);
                            break;
                        case ParsingExpires:
                            expires = setCookieString.subSequence(begin, i);
                            break;
                        case ParsingMaxAge:
                            maxAge = parseLong(setCookieString, begin, i, 10);
                            break;
                        case ParsingSameSite:
                            sameSite = fromSequence(setCookieString, begin, i);
                            break;
                        default:
                            if (name == null) {
                                throw new IllegalArgumentException("cookie name cannot be null or empty");
                            }
                            final CharSequence avName = setCookieString.subSequence(begin, i);
                            if (contentEqualsIgnoreCase(avName, "secure")) {
                                isSecure = true;
                            } else if (contentEqualsIgnoreCase(avName, "httponly")) {
                                isHttpOnly = true;
                            } else if (contentEqualsIgnoreCase(avName, "partitioned")) {
                                isPartitioned = true;
                            }
                            break;
                    }
                    parseState = ParseState.Unknown;
                    if (validateContent && HeaderUtils.cookieParsingStrictRfc6265()) {
                        // Only enable this check in pedantic-mode. At least the Jersey web framework typically doesn't
                        // include the space.
                        if (i + 1 >= length || ' ' != setCookieString.charAt(i + 1)) {
                            throw new IllegalArgumentException("set-cookie '" + name +
                                    "': a space is required after ; in cookie attribute-value lists");
                        }
                        i += 2;
                    } else {
                        i++;
                        if (i < length && ' ' == setCookieString.charAt(i)) {
                            i++;
                        }
                    }
                    begin = i;
                    break;
                default:
                    if (validateContent) {
                        if (parseState == ParseState.ParsingValue) {
                            // Cookie values need to conform to the cookie-octet rule of
                            // https://www.rfc-editor.org/rfc/rfc6265#section-4.1.1
                            validateCookieOctetHexValue(name, c, i);
                        } else {
                            // Cookie attribute-value rules are "any CHAR except CTLs or ';'"
                            validateCookieAttributeValue(name, c, i);
                        }
                    }
                    ++i;
                    break;
            }
        }

        if (begin < i) {
            // end of value, or end of av-value
            // check for "secure" and "httponly"
            switch (parseState) {
                case ParsingValue:
                    value = setCookieString.subSequence(begin, i);
                    break;
                case ParsingPath:
                    path = setCookieString.subSequence(begin, i);
                    break;
                case ParsingDomain:
                    domain = setCookieString.subSequence(begin, i);
                    break;
                case ParsingExpires:
                    expires = setCookieString.subSequence(begin, i);
                    break;
                case ParsingSameSite:
                    sameSite = fromSequence(setCookieString, begin, i);
                    break;
                case ParsingMaxAge:
                    maxAge = parseLong(setCookieString, begin, i, 10);
                    break;
                default:
                    if (name == null) {
                        throw new IllegalArgumentException("set-cookie value not found at index " + i);
                    }
                    final CharSequence avName = setCookieString.subSequence(begin, i);
                    if (contentEqualsIgnoreCase(avName, "secure")) {
                        isSecure = true;
                    } else if (contentEqualsIgnoreCase(avName, "httponly")) {
                        isHttpOnly = true;
                    } else if (contentEqualsIgnoreCase(avName, "partitioned")) {
                        isPartitioned = true;
                    }
                    break;
            }
        } else if (begin == i) {
            switch (parseState) {
            case ParsingValue:
                if (!isWrapped) {
                    // Values can be the empty string, if we had a cookie name and an equals sign, and no quotes.
                    value = "";
                }
                break;
            case ParsingPath:
                path = "";
                break;
            case ParsingDomain:
                domain = "";
                break;
            case Unknown:
                break;
            default:
                throw new Error("Unhandled parse state: " + parseState);
            }
        }

        assert name != null && value != null; // these are checked at runtime in the constructor
        return new DefaultHttpSetCookie(name, value, path, domain, expires, maxAge, sameSite, isWrapped, isSecure,
                isHttpOnly, isPartitioned);
    }

    /**
     * Parse a {@code setCookie} {@link CharSequence} into a {@link HttpSetCookie}.
     *
     * @param setCookieString The <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">set-cookie-string</a>
     * value.
     * @param validateContent {@code true} to make a best effort to validate the contents of the SetCookie.
     * @return a {@link HttpSetCookie} representation of {@code setCookie}.
     */
    public static HttpSetCookie parseSetCookie(final CharSequence setCookieString, boolean validateContent) {
        return parseSetCookie(setCookieString, validateContent, null, 0);
    }

    @Override
    public CharSequence name() {
        return name;
    }

    @Override
    public CharSequence value() {
        return value;
    }

    @Override
    public boolean isWrapped() {
        return wrapped;
    }

    @Nullable
    @Override
    public CharSequence domain() {
        return domain;
    }

    @Nullable
    @Override
    public CharSequence path() {
        return path;
    }

    @Nullable
    @Override
    public Long maxAge() {
        return maxAge;
    }

    @Nullable
    @Override
    public CharSequence expires() {
        return expires;
    }

    @Nullable
    @Override
    public SameSite sameSite() {
        return sameSite;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public boolean isHttpOnly() {
        return httpOnly;
    }

    @Override
    public boolean isPartitioned() {
        return partitioned;
    }

    @Override
    public CharSequence encodedCookie() {
        return new DefaultHttpCookiePair(name, value, wrapped).encodedCookie();
    }

    @Override
    public CharSequence encodedSetCookie() {
        StringBuilder sb = new StringBuilder(1 + name.length() + value.length() +
                (wrapped ? 2 : 0) +
                (domain != null ? ENCODED_LABEL_DOMAIN.length() + domain.length() : 0) +
                (path != null ? ENCODED_LABEL_PATH.length() + path.length() : 0) +
                (expires != null ? ENCODED_LABEL_EXPIRES.length() + expires.length() : 0) +
                (maxAge != null ? ENCODED_LABEL_MAX_AGE.length() + 11 : 0) +
                (sameSite != null ? ENCODED_LABEL_SAMESITE.length() + SameSite.Strict.toString().length() : 0) +
                (httpOnly ? ENCODED_LABEL_HTTP_ONLY.length() : 0) +
                (secure ? ENCODED_LABEL_SECURE.length() : 0) +
                (partitioned ? ENCODED_LABEL_PARTITIONED.length() : 0));
        sb.append(name).append('=');
        if (wrapped) {
            sb.append('"').append(value).append('"');
        } else {
            sb.append(value);
        }
        if (domain != null) {
            sb.append(ENCODED_LABEL_DOMAIN);
            sb.append(domain);
        }
        if (path != null) {
            sb.append(ENCODED_LABEL_PATH);
            sb.append(path);
        }
        if (expires != null) {
            sb.append(ENCODED_LABEL_EXPIRES);
            sb.append(expires);
        }
        if (maxAge != null) {
            sb.append(ENCODED_LABEL_MAX_AGE);
            sb.append(maxAge);
        }
        if (sameSite != null) {
            sb.append(ENCODED_LABEL_SAMESITE);
            sb.append(sameSite);
        }
        if (httpOnly) {
            sb.append(ENCODED_LABEL_HTTP_ONLY);
        }
        if (secure) {
            sb.append(ENCODED_LABEL_SECURE);
        }
        if (partitioned) {
            sb.append(ENCODED_LABEL_PARTITIONED);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpSetCookie)) {
            return false;
        }
        final HttpSetCookie rhs = (HttpSetCookie) o;
        // It is not possible to do domain [1] and path [2] equality and preserve the equals/hashCode API because the
        // equality comparisons in the RFC are variable so we cannot guarantee the following property:
        // if equals(a) == equals(b) then a.hasCode() == b.hashCode()
        // [1] https://tools.ietf.org/html/rfc6265#section-5.1.3
        // [2] https://tools.ietf.org/html/rfc6265#section-5.1.4
        return contentEquals(name, rhs.name()) &&
               contentEquals(value, rhs.value()) &&
               contentEqualsIgnoreCase(domain, rhs.domain()) &&
               contentEquals(path, rhs.path());
    }

    @Override
    public int hashCode() {
        int hash = 31 + AsciiString.hashCode(name);
        hash = 31 * hash + AsciiString.hashCode(value);
        if (domain != null) {
            hash = 31 * hash + AsciiString.hashCode(domain);
        }
        if (path != null) {
            hash = 31 * hash + AsciiString.hashCode(path);
        }
        return hash;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '[' + name + ']';
    }

    private enum ParseState {
        ParsingValue,
        ParsingPath,
        ParsingDomain,
        ParsingExpires,
        ParsingMaxAge,
        ParsingSameSite,
        Unknown
    }

    @Nullable
    private static SameSite fromSequence(CharSequence cs, int begin, int end) {
        switch (end - begin) {
            case 3:
                if (equalsIgnoreCaseLower(cs.charAt(begin), 'l') &&
                    equalsIgnoreCaseLower(cs.charAt(begin + 1), 'a') &&
                    equalsIgnoreCaseLower(cs.charAt(begin + 2), 'x')) {
                    return SameSite.Lax;
                }
                break;
            case 4:
                if (equalsIgnoreCaseLower(cs.charAt(begin), 'n') &&
                    equalsIgnoreCaseLower(cs.charAt(begin + 1), 'o') &&
                    equalsIgnoreCaseLower(cs.charAt(begin + 2), 'n') &&
                    equalsIgnoreCaseLower(cs.charAt(begin + 3), 'e')) {
                    return SameSite.None;
                }
                break;
            case 6:
                if (equalsIgnoreCaseLower(cs.charAt(begin), 's') &&
                    equalsIgnoreCaseLower(cs.charAt(begin + 1), 't') &&
                    equalsIgnoreCaseLower(cs.charAt(begin + 2), 'r') &&
                    equalsIgnoreCaseLower(cs.charAt(begin + 3), 'i') &&
                    equalsIgnoreCaseLower(cs.charAt(begin + 4), 'c') &&
                    equalsIgnoreCaseLower(cs.charAt(begin + 5), 't')) {
                    return SameSite.Strict;
                }
                break;
            default:
                break;
        }
        return null;
    }

    private static boolean equalsIgnoreCaseLower(char c, char k) {
        return c == k || c >= 'A' && c <= 'Z' && c == k - 32;
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">
     * cookie-octet = %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E</a>
     *
     * @param hexValue The decimal representation of the hexadecimal value.
     * @param index The index of the character in the inputs, for error reporting.
     */
    private static void validateCookieOctetHexValue(final CharSequence name, final int hexValue, int index) {
        if (hexValue != 33 &&
                (hexValue < 35 || hexValue > 43) &&
                (hexValue < 45 || hexValue > 58) &&
                (hexValue < 60 || hexValue > 91) &&
                (hexValue < 93 || hexValue > 126)) {
            throw unexpectedHexValue(name, hexValue, index);
        }
    }

    /**
     * Attribute values are <a href="https://www.rfc-editor.org/rfc/rfc6265#section-4.1.1">
     *     any CHAR except CTLs or ";"</a>,
     * and CTLs are <a href="https://www.rfc-editor.org/rfc/rfc5234#appendix-B.1">%x00 to %x1F, and %x7F</a>.
     *
     * @param hexValue The decimal representation of the hexadecimal value.
     * @param index The index of the character in the inputs, for error reporting.
     */
    private static void validateCookieAttributeValue(final CharSequence name, final int hexValue, int index) {
        if (hexValue == ';' || hexValue == 0x7F || hexValue <= 0x1F) {
            throw unexpectedHexValue(name, hexValue, index);
        }
    }

    @NotNull
    private static IllegalArgumentException unexpectedHexValue(final CharSequence name, int hexValue, int index) {
        return new IllegalArgumentException(
                "set-cookie '" + name + "': unexpected hex value at index " + index + ": 0x" + toHexString(hexValue));
    }
}
