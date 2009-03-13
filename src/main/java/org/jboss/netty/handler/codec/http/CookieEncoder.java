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

import org.jboss.netty.util.CaseIgnoringComparator;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @version $Rev$, $Date$
 */
public class CookieEncoder {

    private final Map<String, Cookie> cookies = new TreeMap<String, Cookie>(CaseIgnoringComparator.INSTANCE);

    private final String charset;

    private final int encodingVersion;

    public CookieEncoder() {
        this(QueryStringDecoder.DEFAULT_CHARSET, 0);
    }

    public CookieEncoder(int encodingVersion) {
        this(QueryStringDecoder.DEFAULT_CHARSET, encodingVersion);
    }

    public CookieEncoder(String charset) {
        this(charset, 0);
    }

    public CookieEncoder(String charset, int encodingVersion) {
        if (encodingVersion < 0 || encodingVersion > 2) {
            throw new IllegalArgumentException("encoding version must be 0,1 or 2");
        }
        this.encodingVersion = encodingVersion;
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        this.charset = charset;
    }

    public void addCookie(String name, String val) {
        cookies.put(name, new DefaultCookie(name, val));
    }

    public void addCookie(Cookie cookie) {
        cookies.put(cookie.getName(), cookie);
    }

    public String encode() {
        StringBuffer sb = new StringBuffer();
        Collection<String> cookieNames = cookies.keySet();
        if (cookieNames.isEmpty()) {
            return null;
        }
        for (String cookieName : cookieNames) {
            Cookie cookie = cookies.get(cookieName);
            add(sb, cookieName, QueryStringEncoder.encodeComponent(cookie.getValue(), charset));

            add(sb, CookieHeaderNames.getMaxAgeString(encodingVersion), cookie.getMaxAge());

            if (cookie.getPath() != null) {
                add(sb, CookieHeaderNames.PATH, QueryStringEncoder.encodeComponent(cookie.getPath(), charset));
            }

            if (cookie.getDomain() != null) {
                add(sb, CookieHeaderNames.DOMAIN, QueryStringEncoder.encodeComponent(cookie.getDomain(), charset));
            }
            if (cookie.isSecure()) {
                    sb.append(CookieHeaderNames.SECURE);
                    sb.append((char) HttpCodecUtil.SEMICOLON);
                }
            if (encodingVersion >= 1) {
                if (cookie.getComment() != null) {
                    add(sb, CookieHeaderNames.COMMENT, QueryStringEncoder.encodeComponent(cookie.getComment(), charset));
                }

                add(sb, CookieHeaderNames.VERSION, encodingVersion);
            }

            if (encodingVersion == 2) {
                if (cookie.getCommentURL() != null) {
                    add(sb, CookieHeaderNames.COMMENTURL, QueryStringEncoder.encodeComponent(cookie.getCommentURL(), charset));
                }
                if(cookie.getPortList() != null && cookie.getPortList().length > 0) {
                    sb.append(CookieHeaderNames.PORTLIST);
                    sb.append((char) HttpCodecUtil.EQUALS);
                    for (int i = 0; i < cookie.getPortList().length; i++) {
                        int port = cookie.getPortList()[i];
                        if(i > 0) {
                            sb.append((char)HttpCodecUtil.COMMA);
                        }
                        sb.append(port);
                    }
                    sb.append((char) HttpCodecUtil.SEMICOLON);
                }
                if (cookie.isDiscard()) {
                    sb.append(CookieHeaderNames.DISCARD);
                    sb.append((char) HttpCodecUtil.SEMICOLON);
                }
            }
        }
        return sb.toString();
    }

    private void add(StringBuffer sb, String name, String val) {
        sb.append(name);
        sb.append((char) HttpCodecUtil.EQUALS);
        sb.append(val);
        sb.append((char) HttpCodecUtil.SEMICOLON);
    }

    private void add(StringBuffer sb, String name, int val) {
        sb.append(name);
        sb.append((char) HttpCodecUtil.EQUALS);
        sb.append(val);
        sb.append((char) HttpCodecUtil.SEMICOLON);
    }
}
