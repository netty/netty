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

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class CookieHeaderNames {
    public static final String PATH = "path";

    public static final String EXPIRES = "expires";

    public static final String MAX_AGE = "max-age";

    public static final String DOMAIN = "domain";

    public static final String SECURE = "secure";

    public static final String COMMENT = "comment";

    public static final String COMMENTURL = "commentURL";

    public static final String DISCARD = "discard";

    public static final String PORTLIST = "port";

    public static final String VERSION = "version";

    public static String getMaxAgeString(int version) {
        switch (version) {
            case 0:
                return EXPIRES;
            case 1:
                return MAX_AGE;
            case 2:
                return MAX_AGE;
            default:
                return EXPIRES;
        }
    }
}
