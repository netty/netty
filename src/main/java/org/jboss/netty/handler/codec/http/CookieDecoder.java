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
package org.jboss.netty.handler.codec.http;

import static org.jboss.netty.handler.codec.http.CookieUtil.firstInvalidCookieNameOctet;
import static org.jboss.netty.handler.codec.http.CookieUtil.firstInvalidCookieValueOctet;
import static org.jboss.netty.handler.codec.http.CookieUtil.unwrapValue;
import org.jboss.netty.handler.codec.http.cookie.CookieHeaderNames;
import org.jboss.netty.util.internal.StringUtil;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * @deprecated Use {@link io.netty.handler.codec.http.cookie.ClientCookieDecoder}
 * or {@link io.netty.handler.codec.http.cookie.ServerCookieDecoder} instead.
 *
 * Decodes an HTTP header value into {@link Cookie}s.  This decoder can decode
 * the HTTP cookie version 0, 1, and 2.
 *
 * <pre>
 * {@link HttpRequest} req = ...;
 * String value = req.getHeader("Cookie");
 * Set&lt;{@link Cookie}&gt; cookies = {@link CookieDecoder}.decode(value);
 * </pre>
 *
 * @see io.netty.handler.codec.http.cookie.ClientCookieDecoder
 * @see io.netty.handler.codec.http.cookie.ServerCookieDecoder
 */
@Deprecated
public final class CookieDecoder {

    private final InternalLogger logger = InternalLoggerFactory.getInstance(getClass());

    private static final String COMMENT = "Comment";

    private static final String COMMENTURL = "CommentURL";

    private static final String DISCARD = "Discard";

    private static final String PORT = "Port";

    private static final String VERSION = "Version";

    private static final char COMMA = ',';

    private final boolean strict;

    /**
     * Creates a new decoder.
     */
    public CookieDecoder() {
        this(false);
    }

    /**
     * Creates a new decoder.
     *
     * @param strict {@code true} if and only if this encoder is supposed to
     *               validate characters according to RFC6265.
     */
    public CookieDecoder(boolean strict) {
        this.strict = strict;
    }

    /**
     * Decodes the specified HTTP header value into {@link Cookie}s.
     *
     * @return the decoded {@link Cookie}s
     */
    public Set<Cookie> decode(String header) {
        List<String> names = new ArrayList<String>(8);
        List<String> values = new ArrayList<String>(8);
        extractKeyValuePairs(header, names, values);

        if (names.isEmpty()) {
            return Collections.emptySet();
        }

        int i;
        int version = 0;

        // $Version is the only attribute that can appear before the actual
        // cookie name-value pair.
        if (names.get(0).equalsIgnoreCase(VERSION)) {
            try {
                version = Integer.parseInt(values.get(0));
            } catch (NumberFormatException e) {
                // Ignore.
            }
            i = 1;
        } else {
            i = 0;
        }

        if (names.size() <= i) {
            // There's a version attribute, but nothing more.
            return Collections.emptySet();
        }

        Set<Cookie> cookies = new TreeSet<Cookie>();
        for (; i < names.size(); i ++) {
            String name = names.get(i);
            String value = values.get(i);
            if (value == null) {
                value = "";
            }

            Cookie c = initCookie(name, value);

            if (c == null) {
                break;
            }

            boolean discard = false;
            boolean secure = false;
            boolean httpOnly = false;
            String comment = null;
            String commentURL = null;
            String domain = null;
            String path = null;
            int maxAge = Integer.MIN_VALUE;
            List<Integer> ports = new ArrayList<Integer>(2);

            for (int j = i + 1; j < names.size(); j++, i++) {
                name = names.get(j);
                value = values.get(j);

                if (DISCARD.equalsIgnoreCase(name)) {
                    discard = true;
                } else if (CookieHeaderNames.SECURE.equalsIgnoreCase(name)) {
                    secure = true;
                } else if (CookieHeaderNames.HTTPONLY.equalsIgnoreCase(name)) {
                   httpOnly = true;
                } else if (COMMENT.equalsIgnoreCase(name)) {
                    comment = value;
                } else if (COMMENTURL.equalsIgnoreCase(name)) {
                    commentURL = value;
                } else if (CookieHeaderNames.DOMAIN.equalsIgnoreCase(name)) {
                    domain = value;
                } else if (CookieHeaderNames.PATH.equalsIgnoreCase(name)) {
                    path = value;
                } else if (CookieHeaderNames.EXPIRES.equalsIgnoreCase(name)) {
                    try {
                        long maxAgeMillis =
                            HttpHeaderDateFormat.get().parse(value).getTime() -
                            System.currentTimeMillis();

                        maxAge = (int) (maxAgeMillis / 1000 + (maxAgeMillis % 1000 != 0? 1 : 0));
                    } catch (ParseException e) {
                        // Ignore.
                    }
                } else if (CookieHeaderNames.MAX_AGE.equalsIgnoreCase(name)) {
                    maxAge = Integer.parseInt(value);
                } else if (VERSION.equalsIgnoreCase(name)) {
                    version = Integer.parseInt(value);
                } else if (PORT.equalsIgnoreCase(name)) {
                    String[] portList = StringUtil.split(value, COMMA);
                    for (String s1: portList) {
                        try {
                            ports.add(Integer.valueOf(s1));
                        } catch (NumberFormatException e) {
                            // Ignore.
                        }
                    }
                } else {
                    break;
                }
            }

            c.setVersion(version);
            c.setMaxAge(maxAge);
            c.setPath(path);
            c.setDomain(domain);
            c.setSecure(secure);
            c.setHttpOnly(httpOnly);
            if (version > 0) {
                c.setComment(comment);
            }
            if (version > 1) {
                c.setCommentUrl(commentURL);
                c.setPorts(ports);
                c.setDiscard(discard);
            }

            cookies.add(c);
        }

        return cookies;
    }

    private static void extractKeyValuePairs(
            final String header, final List<String> names, final List<String> values) {
        final int headerLen  = header.length();
        loop: for (int i = 0;;) {

            // Skip spaces and separators.
            for (;;) {
                if (i == headerLen) {
                    break loop;
                }
                switch (header.charAt(i)) {
                case '\t': case '\n': case 0x0b: case '\f': case '\r':
                case ' ':  case ',':  case ';':
                    i ++;
                    continue;
                }
                break;
            }

            // Skip '$'.
            for (;;) {
                if (i == headerLen) {
                    break loop;
                }
                if (header.charAt(i) == '$') {
                    i ++;
                    continue;
                }
                break;
            }

            String name;
            String value;

            if (i == headerLen) {
                name = null;
                value = null;
            } else {
                int newNameStart = i;
                keyValLoop: for (;;) {
                    switch (header.charAt(i)) {
                    case ';':
                        // NAME; (no value till ';')
                        name = header.substring(newNameStart, i);
                        value = null;
                        break keyValLoop;
                    case '=':
                        // NAME=VALUE
                        name = header.substring(newNameStart, i);
                        i ++;
                        if (i == headerLen) {
                            // NAME= (empty value, i.e. nothing after '=')
                            value = "";
                            break keyValLoop;
                        }

                        int newValueStart = i;
                        char c = header.charAt(i);
                        if (c == '"' || c == '\'') {
                            // NAME="VALUE" or NAME='VALUE'
                            StringBuilder newValueBuf = new StringBuilder(header.length() - i);
                            final char q = c;
                            boolean hadBackslash = false;
                            i ++;
                            for (;;) {
                                if (i == headerLen) {
                                    value = newValueBuf.toString();
                                    break keyValLoop;
                                }
                                if (hadBackslash) {
                                    hadBackslash = false;
                                    c = header.charAt(i ++);
                                    switch (c) {
                                    case '\\': case '"': case '\'':
                                        // Escape last backslash.
                                        newValueBuf.setCharAt(newValueBuf.length() - 1, c);
                                        break;
                                    default:
                                        // Do not escape last backslash.
                                        newValueBuf.append(c);
                                    }
                                } else {
                                    c = header.charAt(i ++);
                                    if (c == q) {
                                        value = newValueBuf.toString();
                                        break keyValLoop;
                                    }
                                    newValueBuf.append(c);
                                    if (c == '\\') {
                                        hadBackslash = true;
                                    }
                                }
                            }
                        } else {
                            // NAME=VALUE;
                            int semiPos = header.indexOf(';', i);
                            if (semiPos > 0) {
                                value = header.substring(newValueStart, semiPos);
                                i = semiPos;
                            } else {
                                value = header.substring(newValueStart);
                                i = headerLen;
                            }
                        }
                        break keyValLoop;
                    default:
                        i ++;
                    }

                    if (i == headerLen) {
                        // NAME (no value till the end of string)
                        name = header.substring(newNameStart);
                        value = null;
                        break;
                    }
                }
            }

            names.add(name);
            values.add(value);
        }
    }

    private DefaultCookie initCookie(String name, String value) {
        if (name == null || name.length() == 0) {
            logger.debug("Skipping cookie with null name");
            return null;
        }

        if (value == null) {
            logger.debug("Skipping cookie with null value");
            return null;
        }

        CharSequence unwrappedValue = unwrapValue(value);
        if (unwrappedValue == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Skipping cookie because starting quotes are not properly balanced in '"
                    + unwrappedValue + "'");
            }
            return null;
        }

        int invalidOctetPos;
        if (strict && (invalidOctetPos = firstInvalidCookieNameOctet(name)) >= 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("Skipping cookie because name '" + name + "' contains invalid char '"
                    + name.charAt(invalidOctetPos) + "'");
            }
            return null;
        }

        final boolean wrap = unwrappedValue.length() != value.length();

        if (strict && (invalidOctetPos = firstInvalidCookieValueOctet(unwrappedValue)) >= 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("Skipping cookie because value '" + unwrappedValue
                    + "' contains invalid char '" + unwrappedValue.charAt(invalidOctetPos) + "'");
            }
            return null;
        }

        DefaultCookie cookie = new DefaultCookie(name, unwrappedValue.toString());
        cookie.setWrap(wrap);
        return cookie;
    }
}
