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

import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class CookieEncoder {

    private final Set<Cookie> cookies = new TreeSet<Cookie>();
    private final boolean server;
    private final String charset;

    public CookieEncoder(boolean server) {
        this(server, QueryStringDecoder.DEFAULT_CHARSET);
    }

    public CookieEncoder(boolean server, String charset) {
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        this.server = server;
        this.charset = charset;
    }

    public void addCookie(String name, String value) {
        cookies.add(new DefaultCookie(name, value));
    }

    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    public String encode() {
        if (server) {
            return encodeServerSide();
        } else {
            return encodeClientSide();
        }
    }

    private String encodeServerSide() {
        StringBuilder sb = new StringBuilder();

        for (Cookie cookie: cookies) {
            add(sb, cookie.getName(), QueryStringEncoder.encodeComponent(cookie.getValue(), charset));

            if (cookie.getMaxAge() >= 0) {
                if (cookie.getVersion() == 0) {
                    add(sb, CookieHeaderNames.EXPIRES,
                            new CookieDateFormat().format(
                                    new Date(System.currentTimeMillis() +
                                             cookie.getMaxAge() * 1000L)));
                } else {
                    add(sb, CookieHeaderNames.MAX_AGE, cookie.getMaxAge());
                }
            }

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
            if (cookie.getVersion() >= 1) {
                if (cookie.getComment() != null) {
                    add(sb, CookieHeaderNames.COMMENT, cookie.getComment());
                }

                add(sb, CookieHeaderNames.VERSION, 1);

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

    private String encodeClientSide() {
        StringBuilder sb = new StringBuilder();

        for (Cookie cookie: cookies) {
            if (cookie.getVersion() >= 1) {
                add(sb, '$' + CookieHeaderNames.VERSION, 1);
            }

            add(sb, cookie.getName(), QueryStringEncoder.encodeComponent(cookie.getValue(), charset));

            if (cookie.getPath() != null) {
                add(sb, '$' + CookieHeaderNames.PATH, cookie.getPath());
            }

            if (cookie.getDomain() != null) {
                add(sb, '$' + CookieHeaderNames.DOMAIN, cookie.getDomain());
            }

            if (cookie.getVersion() >= 1) {
                if(!cookie.getPorts().isEmpty()) {
                    sb.append('$');
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
            }
        }
        return sb.toString();
    }

    private static void add(StringBuilder sb, String name, String val) {
        sb.append(name);
        sb.append((char) HttpCodecUtil.EQUALS);
        sb.append(val);
        sb.append((char) HttpCodecUtil.SEMICOLON);
    }

    private static void addQuoted(StringBuilder sb, String name, String val) {
        sb.append(name);
        sb.append((char) HttpCodecUtil.EQUALS);
        sb.append((char) HttpCodecUtil.DOUBLE_QUOTE);
        sb.append(val);
        sb.append((char) HttpCodecUtil.DOUBLE_QUOTE);
        sb.append((char) HttpCodecUtil.SEMICOLON);
    }

    private static void add(StringBuilder sb, String name, int val) {
        sb.append(name);
        sb.append((char) HttpCodecUtil.EQUALS);
        sb.append(val);
        sb.append((char) HttpCodecUtil.SEMICOLON);
    }
}
