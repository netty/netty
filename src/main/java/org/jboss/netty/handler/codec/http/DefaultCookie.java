/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;



/**
 * The default {@link Cookie} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
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
        RESERVED_NAMES.add("HTTPOnly");
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
    private boolean httpOnly;

    /**
     * Creates a new cookie with the specified name and value.
     */
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
            case '\t': case '\n': case 0x0b: case '\f': case '\r':
            case ' ':  case ',':  case ';':  case '=':
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

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
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
        if (isHttpOnly()) {
            buf.append(", HTTPOnly");
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
