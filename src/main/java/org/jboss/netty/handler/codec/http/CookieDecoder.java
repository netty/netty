/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.http;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decodes an HTTP header value into {@link Cookie}s.  This decoder can decode
 * the HTTP cookie version 0, 1, and 2.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 * @see CookieEncoder
 *
 * @apiviz.stereotype utility
 * @apiviz.has        org.jboss.netty.handler.codec.http.Cookie oneway - - decodes
 */
public class CookieDecoder {

    private final static Pattern PATTERN =
        Pattern.compile("(?:\\s|[;,])*\\$*([^;=]+)(?:=(?:[\"']((?:\\\\.|[^\"])*)[\"']|([^;,]*)))?\\s*(?:[;,]+|$)");

    private final static String COMMA = ",";

    /**
     * Creates a new decoder.
     */
    public CookieDecoder() {
        super();
    }

    /**
     * Decodes the specified HTTP header value into {@link Cookie}s.
     *
     * @return the decoded {@link Cookie}s
     */
    public Set<Cookie> decode(String header) {
        Matcher m = PATTERN.matcher(header);
        List<String> names = new ArrayList<String>(8);
        List<String> values = new ArrayList<String>(8);
        int pos = 0;
        int version = 0;
        while (m.find(pos)) {
            pos = m.end();

            // Extract name and value pair from the match.
            String name = m.group(1);
            String value = m.group(3);
            if (value == null) {
                value = decodeValue(m.group(2));
            }

            // An exceptional case:
            // 'Expires' attribute can contain a comma without surrounded with quotes.
            if (name.equalsIgnoreCase(CookieHeaderNames.EXPIRES) &&
                value.length() <= 3) {
                // value contains comma, but not surrounded with quotes.
                if (m.find(pos)) {
                    value = value + ", " + m.group(1);
                    pos = m.end();
                } else {
                    continue;
                }
            }

            names.add(name);
            values.add(value);
        }

        if (names.isEmpty()) {
            return Collections.emptySet();
        }

        int i;

        // $Version is the only attribute that can appear before the actual
        // cookie name-value pair.
        if (names.get(0).equalsIgnoreCase(CookieHeaderNames.VERSION)) {
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

            Cookie c = new DefaultCookie(name, value);
            cookies.add(c);

            boolean discard = false;
            boolean secure = false;
            String comment = null;
            String commentURL = null;
            String domain = null;
            String path = null;
            int maxAge = -1;
            List<Integer> ports = new ArrayList<Integer>(2);

            for (int j = i + 1; j < names.size(); j++, i++) {
                name = names.get(j);
                value = values.get(j);

                if (CookieHeaderNames.DISCARD.equalsIgnoreCase(name)) {
                    discard = true;
                } else if (CookieHeaderNames.SECURE.equalsIgnoreCase(name)) {
                    secure = true;
                } else if (CookieHeaderNames.COMMENT.equalsIgnoreCase(name)) {
                    comment = value;
                } else if (CookieHeaderNames.COMMENTURL.equalsIgnoreCase(name)) {
                    commentURL = value;
                } else if (CookieHeaderNames.DOMAIN.equalsIgnoreCase(name)) {
                    domain = value;
                } else if (CookieHeaderNames.PATH.equalsIgnoreCase(name)) {
                    path = value;
                } else if (CookieHeaderNames.EXPIRES.equalsIgnoreCase(name)) {
                    try {
                        long maxAgeMillis =
                            new CookieDateFormat().parse(value).getTime() -
                            System.currentTimeMillis();
                        if (maxAgeMillis <= 0) {
                            maxAge = 0;
                        } else {
                            maxAge = (int) (maxAgeMillis / 1000) +
                                     (maxAgeMillis % 1000 != 0? 1 : 0);
                        }
                    } catch (ParseException e) {
                        // Ignore.
                    }
                } else if (CookieHeaderNames.MAX_AGE.equalsIgnoreCase(name)) {
                    maxAge = Integer.parseInt(value);
                } else if (CookieHeaderNames.VERSION.equalsIgnoreCase(name)) {
                    version = Integer.parseInt(value);
                } else if (CookieHeaderNames.PORT.equalsIgnoreCase(name)) {
                    String[] portList = value.split(COMMA);
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
            if (version > 0) {
                c.setComment(comment);
            }
            if (version > 1) {
                c.setCommentUrl(commentURL);
                c.setPorts(ports);
                c.setDiscard(discard);
            }
        }

        return cookies;
    }

    private String decodeValue(String value) {
        if (value == null) {
            return value;
        }
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
