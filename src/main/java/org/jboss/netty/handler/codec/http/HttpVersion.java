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
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 *
 * @apiviz.exclude
 */
public class HttpVersion implements Comparable<HttpVersion> {

    public static final HttpVersion HTTP_1_0 = new HttpVersion("HTTP", 1, 0);
    public static final HttpVersion HTTP_1_1 = new HttpVersion("HTTP", 1, 1);

    private static final java.util.regex.Pattern VERSION_PATTERN =
            java.util.regex.Pattern.compile("(\\S+)/(\\d+)\\.(\\d+)");

    public static HttpVersion valueOf(String value) {
        value = value.toUpperCase();
        if (value.equals("HTTP/1.1")) {
            return HTTP_1_1;
        }
        if (value.equals("HTTP/1.0")) {
            return HTTP_1_0;
        }
        return new HttpVersion(value);
    }

    private final String protocolName;
    private final int majorVersion;
    private final int minorVersion;
    private final String string;

    public HttpVersion(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        java.util.regex.Matcher m = VERSION_PATTERN.matcher(value);
        if (!m.matches()) {
            throw new IllegalArgumentException("invalid version format: " + value);
        }

        this.protocolName = m.group(1);
        this.majorVersion = Integer.parseInt(m.group(2));
        this.minorVersion = Integer.parseInt(m.group(3));
        this.string = protocolName + '/' + majorVersion + '.' + minorVersion;
    }

    public HttpVersion(
            String protocolName, int majorVersion, int minorVersion) {
        if (protocolName == null) {
            throw new NullPointerException("protocolName");
        }

        protocolName = protocolName.trim().toUpperCase();
        if (protocolName.length() == 0) {
            throw new IllegalArgumentException("empty protocolName");
        }

        for (int i = 0; i < protocolName.length(); i ++) {
            if (Character.isWhitespace(protocolName.charAt(i))) {
                throw new IllegalArgumentException("whitespace in protocolName");
            }
        }

        if (majorVersion < 0) {
            throw new IllegalArgumentException("negative majorVersion");
        }
        if (minorVersion < 0) {
            throw new IllegalArgumentException("negative minorVersion");
        }

        this.protocolName = protocolName;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.string = protocolName + '/' + majorVersion + '.' + minorVersion;
    }

    public String getProtocolName() {
        return protocolName;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    @Override
    public String toString() {
        return string;
    }

    @Override
    public int hashCode() {
        return (getProtocolName().hashCode() * 31 + getMajorVersion()) * 31 +
               getMinorVersion();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpVersion)) {
            return false;
        }

        HttpVersion that = (HttpVersion) o;
        return getMinorVersion() == that.getMinorVersion() &&
               getMajorVersion() == that.getMajorVersion() &&
               getProtocolName().equals(that.getProtocolName());
    }

    public int compareTo(HttpVersion o) {
        int v = getProtocolName().compareTo(o.getProtocolName());
        if (v != 0) {
            return v;
        }

        v = getMajorVersion() - o.getMajorVersion();
        if (v != 0) {
            return v;
        }

        return getMinorVersion() - o.getMinorVersion();
    }
}
