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
 * @version $Rev$, $Date$
 */
public class CookieEncoder {

    // TODO: Add domain, path, maxAge, and version (and perhaps secure and comment?)
    private final Map<String, Cookie> cookies = new TreeMap<String, Cookie>(CaseIgnoringComparator.INSTANCE);

    public void addCookie(String name, String val) {
        cookies.put(name, new DefaultCookie(name, val));
    }

    public void addCookie(Cookie cookie) {
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
            sb.append((char) HttpCodecUtil.EQUALS);
            sb.append(cookies.get(cookieName).getValue());
            sb.append((char) HttpCodecUtil.SEMICOLON);
        }
        return sb.toString();
    }
}
