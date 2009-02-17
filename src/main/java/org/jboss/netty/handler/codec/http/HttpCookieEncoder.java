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
import static org.jboss.netty.handler.codec.http.HttpCodecUtil.COLON;
import static org.jboss.netty.handler.codec.http.HttpCodecUtil.SP;
import static org.jboss.netty.handler.codec.http.HttpCodecUtil.EQUALS;
import static org.jboss.netty.handler.codec.http.HttpCodecUtil.SEMICOLON;
import static org.jboss.netty.handler.codec.http.HttpCodecUtil.CRLF;

import java.util.Map;
import java.util.TreeMap;
import java.util.Comparator;
import java.util.Collection;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class HttpCookieEncoder {
    private final static String semicolon = ";";

    private final static String equals = "=";

    private final static Comparator<String> caseIgnoringComparator = new CaseIgnoringComparator();

    private final Map<String, HttpCookie> cookies = new TreeMap<String, HttpCookie>(caseIgnoringComparator);

    public void addCookie(String name, String val) {
        cookies.put(name, new HttpCookie(name, val));
    }

    public void addCookie(HttpCookie cookie) {
        cookies.put(cookie.getName(), cookie);
    }

    public String encode() {
        StringBuffer sb = new StringBuffer();
        Collection<String> cookieNames = cookies.keySet();
        if(cookieNames.isEmpty()) {
            return null;
        }
        for (String cookieName : cookieNames) {
            sb.append(cookieName);
            sb.append(equals);
            sb.append(cookies.get(cookieName).getValue());
            sb.append(semicolon);
        }
        return sb.toString();
    }
}
