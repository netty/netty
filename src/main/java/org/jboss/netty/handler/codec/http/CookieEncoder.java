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

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.jboss.netty.util.CaseIgnoringComparator;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
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

            // FIXME: Expires attribute has different representation from Max-Age.
            //        Format: Wdy, DD-Mon-YYYY HH:MM:SS GMT
            add(sb, CookieHeaderNames.getMaxAgeString(encodingVersion), cookie.getMaxAge());

            if (cookie.getPath() != null) {
                add(sb, CookieHeaderNames.PATH, cookie.getPath());
            }

            if (cookie.getDomain() != null) {
                add(sb, CookieHeaderNames.DOMAIN, cookie.getDomain());
            }
            if (cookie.isSecure()) {
                    sb.append(CookieHeaderNames.SECURE);
                    sb.append((char) HttpCodecUtil.SEMICOLON);
                }
            if (encodingVersion >= 1) {
                if (cookie.getComment() != null) {
                    add(sb, CookieHeaderNames.COMMENT, cookie.getComment());
                }

                add(sb, CookieHeaderNames.VERSION, 1);
            }

            if (encodingVersion == 2) {
                if (cookie.getCommentUrl() != null) {
                    addQuoted(sb, CookieHeaderNames.COMMENTURL, cookie.getCommentUrl());
                }
                if(!cookie.getPorts().isEmpty()) {
                    sb.append(CookieHeaderNames.PORT);
                    sb.append((char) HttpCodecUtil.EQUALS);
                    sb.append((char) HttpCodecUtil.DOUBLE_QUOTE);
                    for (int port: cookie.getPorts()) {
                        sb.append(port);
                        sb.append((char) HttpCodecUtil.COMMA);
                    }
                    sb.setCharAt(sb.length() - 1, (char) HttpCodecUtil.DOUBLE_QUOTE);
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

    private void addQuoted(StringBuffer sb, String name, String val) {
        sb.append(name);
        sb.append((char) HttpCodecUtil.EQUALS);
        sb.append((char) HttpCodecUtil.DOUBLE_QUOTE);
        sb.append(val);
        sb.append((char) HttpCodecUtil.DOUBLE_QUOTE);
        sb.append((char) HttpCodecUtil.SEMICOLON);
    }

    private void add(StringBuffer sb, String name, int val) {
        sb.append(name);
        sb.append((char) HttpCodecUtil.EQUALS);
        sb.append(val);
        sb.append((char) HttpCodecUtil.SEMICOLON);
    }
}
