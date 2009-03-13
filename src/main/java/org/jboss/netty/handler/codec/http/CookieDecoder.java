/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
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

import java.util.Map;
import java.util.TreeMap;

import org.jboss.netty.util.CaseIgnoringComparator;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class CookieDecoder {

    private final static String SEMICOLON = ";";

    private final static String EQUALS = "=";

    private final static String COMMA = ",";

    private final String charset;

    public CookieDecoder() {
        this(QueryStringDecoder.DEFAULT_CHARSET);
    }

    public CookieDecoder(String charset) {
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        this.charset = charset;
    }

    public Map<String, Cookie> decode(String header) {
        Map<String, Cookie> cookies = new TreeMap<String, Cookie>(CaseIgnoringComparator.INSTANCE);
        String[] split = header.split(SEMICOLON);
        for (int i = 0; i < split.length; i++) {
            DefaultCookie theCookie;
            String s = split[i];
            String[] cookie = s.split(EQUALS, 2);
            if (cookie != null && cookie.length == 2) {
                String name = cookie[0].trim();
                String value = QueryStringDecoder.decodeComponent(cookie[1], charset);
                theCookie = new DefaultCookie(name, value);
                cookies.put(name, theCookie);
                boolean discard = false;
                boolean secure = false;
                String comment = null;
                String commentURL = null;
                String domain = null;
                String path = null;
                int version = 0;
                int maxAge = 0;
                int[] ports = null;
                loop:
                for (int j = i + 1; j < split.length; j++, i++) {
                    String[] val = split[j].split(EQUALS, 2);
                    if (val == null) {
                        continue;
                    }
                    switch (val.length) {
                    case 1:
                        if (CookieHeaderNames.DISCARD.equalsIgnoreCase(val[0])) {
                            discard = true;
                        }
                        else if (CookieHeaderNames.SECURE.equalsIgnoreCase(val[0])) {
                            secure = true;
                        }
                        break;
                    case 2:
                        name = val[0].trim();
                        value = val[1].trim();
                        if (CookieHeaderNames.COMMENT.equalsIgnoreCase(name)) {
                            comment = value;
                        }
                        else if (CookieHeaderNames.COMMENTURL.equalsIgnoreCase(name)) {
                            value = trimSurroundingQuotes(value);
                            commentURL = value;
                        }
                        else if (CookieHeaderNames.DOMAIN.equalsIgnoreCase(name)) {
                            domain = value;
                        }
                        else if (CookieHeaderNames.PATH.equalsIgnoreCase(name)) {
                            path = value;
                        }
                        else if (CookieHeaderNames.EXPIRES.equalsIgnoreCase(name)) {
                            // FIXME: Expires attribute has different representation from Max-Age.
                            //        Format: Wdy, DD-Mon-YYYY HH:MM:SS GMT
                            maxAge = Integer.valueOf(value);
                        }
                        else if (CookieHeaderNames.MAX_AGE.equalsIgnoreCase(name)) {
                            maxAge = Integer.valueOf(value);
                        }
                        else if (CookieHeaderNames.VERSION.equalsIgnoreCase(name)) {
                            version = Integer.valueOf(value);
                        }
                        else if (CookieHeaderNames.PORT.equalsIgnoreCase(name)) {
                            value = trimSurroundingQuotes(value);
                            String[] portList = value.split(COMMA);
                            ports = new int[portList.length];
                            for (int i1 = 0; i1 < portList.length; i1++) {
                                String s1 = portList[i1];
                                ports[i1] = Integer.valueOf(s1);
                            }
                        } else {
                            break loop;
                        }
                        break;
                    }
                }
                theCookie.setVersion(version);
                theCookie.setMaxAge(maxAge);
                theCookie.setPath(path);
                theCookie.setDomain(domain);
                theCookie.setSecure(secure);
                if (version > 0) {
                    theCookie.setComment(comment);
                }
                if (version > 1) {
                    theCookie.setCommentUrl(commentURL);
                    if (ports != null) {
                        theCookie.setPorts(ports);
                    }
                    theCookie.setDiscard(discard);
                }
            }
        }
        return cookies;
    }

    private String trimSurroundingQuotes(String value) {
        if (value.length() >= 2) {
            char firstChar = value.charAt(0);
            char lastChar = value.charAt(value.length() - 1);
            if ((firstChar == '"' || firstChar == '\'') &&
                (lastChar == '"' || lastChar == '\'')) {
                // Strip surrounding quotes.
                value = value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
