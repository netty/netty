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
 * The protocols we support;
 *
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public enum HttpVersion {
    HTTP_1_1("HTTP/1.1"),

    HTTP_1_0("HTTP/1.0"),

    UNKNOWN("UNKNOWN"),;

    private String version;

    private HttpVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public static HttpVersion getProtocol(String protocol) {
        if (protocol == null) {
            return UNKNOWN;
        }
        else if (protocol.equals(HTTP_1_0.getVersion())) {
            return HTTP_1_0;
        }
        else if (protocol.equals(HTTP_1_1.getVersion())) {
            return HTTP_1_1;
        }
        else {
            return UNKNOWN;
        }
    }
}
