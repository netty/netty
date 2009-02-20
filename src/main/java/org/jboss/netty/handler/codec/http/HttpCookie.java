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
 * @author Trustin Lee (tlee@redhat.com)
 */
public class HttpCookie implements Comparable<HttpCookie> {

    // TODO: Add domain, path, maxAge, and version (and perhaps secure and comment?)
    private final String name;
    private final String value;

    public HttpCookie(String name, String value) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (value == null) {
            throw new NullPointerException("value");
        }

        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpCookie)) {
            return false;
        }

        return getName().equalsIgnoreCase(((HttpCookie) o).getName());
    }

    public int compareTo(HttpCookie c) {
        return getName().compareToIgnoreCase(c.getName());
    }

    @Override
    public String toString() {
        return getName() + " = " + getValue();
    }
}