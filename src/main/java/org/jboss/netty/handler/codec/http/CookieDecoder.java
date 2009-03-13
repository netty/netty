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

import java.text.ParseException;
import java.util.Set;
import java.util.TreeSet;

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

    public Set<Cookie> decode(String header) {
        Set<Cookie> cookies = new TreeSet<Cookie>();
        String[] split = header.split(SEMICOLON);
        int version = 0;
        boolean versionAtTheBeginning = false;
        for (int i = 0; i < split.length; i++) {
            DefaultCookie theCookie;
            String s = split[i];
            String[] cookie = s.split(EQUALS, 2);
            if (cookie != null && cookie.length == 2) {
                String name = trimName(cookie[0]);
                String value;

                // $Version is the only attribute that can come before the
                // actual cookie name-value pair.
                if (!versionAtTheBeginning &&
                    name.equalsIgnoreCase(CookieHeaderNames.VERSION)) {
                    try {
                        version = Integer.parseInt(trimValue(cookie[1]));
                    } catch (NumberFormatException e) {
                        // Ignore.
                    }
                    versionAtTheBeginning = true;
                    continue;
                }

                // If it's not a version attribute, it's the name-value pair.
                value = QueryStringDecoder.decodeComponent(trimValue(cookie[1]), charset);
                theCookie = new DefaultCookie(name, value);
                cookies.add(theCookie);
                boolean discard = false;
                boolean secure = false;
                String comment = null;
                String commentURL = null;
                String domain = null;
                String path = null;
                int maxAge = -1;
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
                        name = trimName(val[0]);
                        value = trimValue(val[1]);
                        if (CookieHeaderNames.COMMENT.equalsIgnoreCase(name)) {
                            comment = value;
                        }
                        else if (CookieHeaderNames.COMMENTURL.equalsIgnoreCase(name)) {
                            value = trimValue(value);
                            commentURL = value;
                        }
                        else if (CookieHeaderNames.DOMAIN.equalsIgnoreCase(name)) {
                            domain = value;
                        }
                        else if (CookieHeaderNames.PATH.equalsIgnoreCase(name)) {
                            path = value;
                        }
                        else if (CookieHeaderNames.EXPIRES.equalsIgnoreCase(name)) {
                            try {
                                long maxAgeMillis =
                                    new CookieDateFormat().parse(value).getTime() - System.currentTimeMillis();
                                if (maxAgeMillis <= 0) {
                                    maxAge = 0;
                                } else {
                                    maxAge = (int) (maxAgeMillis / 1000) +
                                             (maxAgeMillis % 1000 != 0? 1 : 0);
                                }
                            } catch (ParseException e) {
                                maxAge = 0;
                            }
                        }
                        else if (CookieHeaderNames.MAX_AGE.equalsIgnoreCase(name)) {
                            maxAge = Integer.valueOf(value);
                        }
                        else if (CookieHeaderNames.VERSION.equalsIgnoreCase(name)) {
                            version = Integer.valueOf(value);
                        }
                        else if (CookieHeaderNames.PORT.equalsIgnoreCase(name)) {
                            value = trimValue(value);
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

    private String trimName(String name) {
        name = name.trim();
        if (name.startsWith("$")) {
            return name.substring(1);
        } else {
            return name;
        }
    }

    private String trimValue(String value) {
        value = value.trim();
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
