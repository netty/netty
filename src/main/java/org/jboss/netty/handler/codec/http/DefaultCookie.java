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

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import org.jboss.netty.util.internal.CaseIgnoringComparator;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class DefaultCookie implements Cookie {

    private static final Set<String> RESERVED_NAMES = new TreeSet<String>(CaseIgnoringComparator.INSTANCE);

    static {
        RESERVED_NAMES.add("Domain");
        RESERVED_NAMES.add("Path");
        RESERVED_NAMES.add("Comment");
        RESERVED_NAMES.add("CommentURL");
        RESERVED_NAMES.add("Discard");
        RESERVED_NAMES.add("Port");
        RESERVED_NAMES.add("Max-Age");
        RESERVED_NAMES.add("Expires");
        RESERVED_NAMES.add("Version");
        RESERVED_NAMES.add("Secure");
    }

    private final String name;
    private String value;
    private String domain;
    private String path;
    private String comment;
    private String commentUrl;
    private boolean discard;
    private Set<Integer> ports = Collections.emptySet();
    private Set<Integer> unmodifiablePorts = ports;
    private int maxAge = -1;
    private int version;
    private boolean secure;

    public DefaultCookie(String name, String value) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        name = name.trim();
        if (name.length() == 0) {
            throw new IllegalArgumentException("empty name");
        }

        for (int i = 0; i < name.length(); i ++) {
            char c = name.charAt(i);
            if (c > 127) {
                throw new IllegalArgumentException(
                        "name contains non-ascii character: " + name);
            }

            // Check prohibited characters.
            switch (c) {
            case '=':  case ',':  case ';': case ' ':
            case '\t': case '\r': case '\n': case '\f':
            case 0x0b: // Vertical tab
                throw new IllegalArgumentException(
                        "name contains one of the following prohibited characters: " +
                        "=,; \\t\\r\\n\\v\\f: " + name);
            }
        }

        if (RESERVED_NAMES.contains(name)) {
            throw new IllegalArgumentException("reserved name: " + name);
        }

        this.name = name;
        setValue(value);
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        this.value = value;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = validateValue("domain", domain);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = validateValue("path", path);
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = validateValue("comment", comment);
    }

    public String getCommentUrl() {
        return commentUrl;
    }

    public void setCommentUrl(String commentUrl) {
        this.commentUrl = validateValue("commentUrl", commentUrl);
    }

    public boolean isDiscard() {
        return discard;
    }

    public void setDiscard(boolean discard) {
        this.discard = discard;
    }

    public Set<Integer> getPorts() {
        if (unmodifiablePorts == null) {
            unmodifiablePorts = Collections.unmodifiableSet(ports);
        }
        return unmodifiablePorts;
    }

    public void setPorts(int... ports) {
        if (ports == null) {
            throw new NullPointerException("ports");
        }

        int[] portsCopy = ports.clone();
        if (portsCopy.length == 0) {
            unmodifiablePorts = this.ports = Collections.emptySet();
        } else {
            Set<Integer> newPorts = new TreeSet<Integer>();
            for (int p: portsCopy) {
                if (p <= 0 || p > 65535) {
                    throw new IllegalArgumentException("port out of range: " + p);
                }
                newPorts.add(Integer.valueOf(p));
            }
            this.ports = newPorts;
            unmodifiablePorts = null;
        }
    }

    public void setPorts(Iterable<Integer> ports) {
        Set<Integer> newPorts = new TreeSet<Integer>();
        for (int p: ports) {
            if (p <= 0 || p > 65535) {
                throw new IllegalArgumentException("port out of range: " + p);
            }
            newPorts.add(Integer.valueOf(p));
        }
        if (newPorts.isEmpty()) {
            unmodifiablePorts = this.ports = Collections.emptySet();
        } else {
            this.ports = newPorts;
            unmodifiablePorts = null;
        }
    }

    public int getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(int maxAge) {
        if (maxAge < -1) {
            throw new IllegalArgumentException(
                    "maxAge must be either -1, 0, or a positive integer: " +
                    maxAge);
        }
        this.maxAge = maxAge;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Cookie)) {
            return false;
        }

        Cookie that = (Cookie) o;
        if (!getName().equalsIgnoreCase(that.getName())) {
            return false;
        }

        if (getPath() == null && that.getPath() != null) {
            return false;
        } else if (that.getPath() == null) {
            return false;
        }
        if (!getPath().equals(that.getPath())) {
            return false;
        }

        if (getDomain() == null && that.getDomain() != null) {
            return false;
        } else if (that.getDomain() == null) {
            return false;
        }
        if (!getDomain().equalsIgnoreCase(that.getDomain())) {
            return false;
        }

        return true;
    }

    public int compareTo(Cookie c) {
        int v;
        v = getName().compareToIgnoreCase(c.getName());
        if (v != 0) {
            return v;
        }

        if (getPath() == null && c.getPath() != null) {
            return -1;
        } else if (c.getPath() == null) {
            return 1;
        }
        v = getPath().compareTo(c.getPath());
        if (v != 0) {
            return v;
        }

        if (getDomain() == null && c.getDomain() != null) {
            return -1;
        } else if (c.getDomain() == null) {
            return 1;
        }
        v = getDomain().compareToIgnoreCase(c.getDomain());
        return v;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getName());
        buf.append('=');
        buf.append(getValue());
        if (getDomain() != null) {
            buf.append(", domain=");
            buf.append(getDomain());
        }
        if (getPath() != null) {
            buf.append(", path=");
            buf.append(getPath());
        }
        if (getComment() != null) {
            buf.append(", comment=");
            buf.append(getComment());
        }
        if (getMaxAge() >= 0) {
            buf.append(", maxAge=");
            buf.append(getMaxAge());
            buf.append('s');
        }
        if (isSecure()) {
            buf.append(", secure");
        }
        return buf.toString();
    }

    private static String validateValue(String name, String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        if (value.length() == 0) {
            return null;
        }
        for (int i = 0; i < value.length(); i ++) {
            char c = value.charAt(i);
            switch (c) {
            case '\r': case '\n': case '\f': case 0x0b: case ';':
                throw new IllegalArgumentException(
                        name + " contains one of the following prohibited characters: " +
                        ";\\r\\n\\f\\v (" + value + ')');
            }
        }
        return value;
    }
}