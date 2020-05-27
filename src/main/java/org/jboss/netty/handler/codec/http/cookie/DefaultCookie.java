/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http.cookie;

import org.jboss.netty.handler.codec.http.cookie.CookieHeaderNames.SameSite;

/**
 * The default {@link Cookie} implementation.
 */
public class DefaultCookie implements Cookie {

    private final String name;
    private String value;
    private boolean wrap;
    private String domain;
    private String path;
    private int maxAge = Integer.MIN_VALUE;
    private boolean secure;
    private boolean httpOnly;
    private SameSite sameSite;

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

        if (name.charAt(0) == '$') {
            throw new IllegalArgumentException("name starting with '$' not allowed: " + name);
        }

        this.name = name;
        setValue(value);
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    public void setValue(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        this.value = value;
    }

    public boolean wrap() {
        return wrap;
    }

    public void setWrap(boolean wrap) {
        this.wrap = wrap;
    }

    public String domain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = validateValue("domain", domain);
    }

    public String path() {
        return path;
    }

    public void setPath(String path) {
        this.path = validateValue("path", path);
    }

    public int maxAge() {
        return maxAge;
    }

    public void setMaxAge(int maxAge) {
        this.maxAge = maxAge;
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

    /**
     * Checks to see if this {@link Cookie} can be sent along cross-site requests.
     * For more information, please look
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-05">here</a>
     * @return <b>same-site-flag</b> value
     */
    public SameSite sameSite() {
        return sameSite;
    }

    /**
     * Determines if this this {@link Cookie} can be sent along cross-site requests.
     * For more information, please look
     *  <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-05">here</a>
     * @param sameSite <b>same-site-flag</b> value
     */
    public void setSameSite(SameSite sameSite) {
        this.sameSite = sameSite;
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Cookie)) {
            return false;
        }

        Cookie that = (Cookie) o;
        if (!name().equalsIgnoreCase(that.name())) {
            return false;
        }

        if (path() == null) {
            if (that.path() != null) {
                return false;
            }
        } else if (that.path() == null) {
            return false;
        } else if (!path().equals(that.path())) {
            return false;
        }

        if (domain() == null) {
            if (that.domain() != null) {
                return false;
            }
        } else if (that.domain() == null) {
            return false;
        } else {
            return domain().equalsIgnoreCase(that.domain());
        }

        return true;
    }

    public int compareTo(Cookie c) {
        int v = name().compareToIgnoreCase(c.name());
        if (v != 0) {
            return v;
        }

        if (path() == null) {
            if (c.path() != null) {
                return -1;
            }
        } else if (c.path() == null) {
            return 1;
        } else {
            v = path().compareTo(c.path());
            if (v != 0) {
                return v;
            }
        }

        if (domain() == null) {
            if (c.domain() != null) {
                return -1;
            }
        } else if (c.domain() == null) {
            return 1;
        } else {
            v = domain().compareToIgnoreCase(c.domain());
            return v;
        }

        return 0;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder()
            .append(name())
            .append('=')
            .append(value());
        if (domain() != null) {
            buf.append(", domain=")
               .append(domain());
        }
        if (path() != null) {
            buf.append(", path=")
               .append(path());
        }
        if (maxAge() >= 0) {
            buf.append(", maxAge=")
               .append(maxAge())
               .append('s');
        }
        if (isSecure()) {
            buf.append(", secure");
        }
        if (isHttpOnly()) {
            buf.append(", HTTPOnly");
        }
        if (sameSite() != null) {
            buf.append(", SameSite=").append(sameSite());
        }
        return buf.toString();
    }

    protected String validateValue(String name, String value) {
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
