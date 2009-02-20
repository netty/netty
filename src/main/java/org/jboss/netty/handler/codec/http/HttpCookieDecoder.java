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
 * @version $Rev$, $Date$
 */
public class HttpCookieDecoder {
    private final static String semicolon = ";";

    private final static String equals = "=";

    public Map<String, HttpCookie> decode(String header) {
        Map<String, HttpCookie> cookies = new TreeMap<String, HttpCookie>(CaseIgnoringComparator.INSTANCE);
        String[] split = header.split(semicolon);
        for (String s : split) {
            String[] cookie = s.split(equals);
            if(cookie != null && cookie.length == 2) {
                String name = cookie[0].trim();
                String value = cookie[1].trim();
                cookies.put(name, new HttpCookie(name, value));
            }
        }
        return cookies;
    }
}
