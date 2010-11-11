/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
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
 * <pre>
 * {@link HttpRequest} req = ...;
 * String value = req.getHeader("Cookie");
 * Set&lt;{@link Cookie}&gt; cookies = new {@link CookieDecoder}().decode(value);
 * </pre>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2122 $, $Date: 2010-02-02 11:00:04 +0900 (Tue, 02 Feb 2010) $
 * @see CookieEncoder
 *
 * @apiviz.stereotype utility
 * @apiviz.has        org.jboss.netty.handler.codec.http.Cookie oneway - - decodes
 */
public class CookieDecoder {

    private final static Pattern PATTERN =
        Pattern.compile("(?:\\s|[;,])*\\$*([^;=]+)(?:=(?:[\"']((?:\\\\.|[^\"])*)[\"']|([^;,]*)))?(\\s*(?:[;,]+\\s*|$))");

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
            boolean httpOnly = false;
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
                } else if (CookieHeaderNames.HTTPONLY.equalsIgnoreCase(name)) {
                   httpOnly = true;
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
            c.setHttpOnly(httpOnly);
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

    private void extractKeyValuePairs(
            String header, List<String> names, List<String> values) {
        Matcher m = PATTERN.matcher(header);
        int pos = 0;
        String name = null;
        String value = null;
        String separator = null;
        while (m.find(pos)) {
            pos = m.end();

            // Extract name and value pair from the match.
            String newName = m.group(1);
            String newValue = m.group(3);
            if (newValue == null) {
                newValue = decodeValue(m.group(2));
            }
            String newSeparator = m.group(4);

            if (name == null) {
                name = newName;
                value = newValue == null? "" : newValue;
                separator = newSeparator;
                continue;
            }

            if (newValue == null &&
                !CookieHeaderNames.DISCARD.equalsIgnoreCase(newName) &&
                !CookieHeaderNames.SECURE.equalsIgnoreCase(newName) &&
                !CookieHeaderNames.HTTPONLY.equalsIgnoreCase(newName)) {
                value = value + separator + newName;
                separator = newSeparator;
                continue;
            }

            names.add(name);
            values.add(value);

            name = newName;
            value = newValue;
            separator = newSeparator;
        }

        // The last entry
        if (name != null) {
            names.add(name);
            values.add(value);
        }
    }

    private String decodeValue(String value) {
        if (value == null) {
            return value;
        }
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
