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
package io.netty.handler.codec.http.cookie;

import io.netty.util.AsciiString;

import static io.netty.handler.codec.http.cookie.CookieUtil.*;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link Cookie} implementation.
 */
public class DefaultCookie implements Cookie {

    private final AsciiString name;
    private AsciiString value;
    private boolean wrap;
    private AsciiString domain;
    private AsciiString path;
    private long maxAge = Long.MIN_VALUE;
    private boolean secure;
    private boolean httpOnly;

    /**
     * Creates a new cookie with the specified {@link AsciiString} name and value.
     */
    public DefaultCookie(AsciiString name, AsciiString value) {
        name = checkNotNull(name, "name").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("empty name");
        }
        this.name = name;
        setValue(value);
    }

    /**
     * Creates a new cookie with the specified name and value.
     */
    @Deprecated
    public DefaultCookie(String name, String value) {
        this(new AsciiString(name), new AsciiString(value));
    }

    @Override
    @Deprecated
    public String name() {
        return toStringOrNull(name);
    }

    @Override
    public AsciiString asciiName() {
        return name;
    }

    @Override
    @Deprecated
    public String value() {
        return toStringOrNull(value);
    }

    @Override
    public AsciiString asciiValue() {
        return value;
    }

    @Override
    public void setValue(AsciiString value) {
        this.value = checkNotNull(value, "value");
    }

    @Override
    public void setValue(String value) {
        this.value = checkNotNull(new AsciiString(value), "value");
    }

    @Override
    public boolean wrap() {
        return wrap;
    }

    @Override
    public void setWrap(boolean wrap) {
        this.wrap = wrap;
    }

    @Override
    @Deprecated
    public String domain() {
        return toStringOrNull(domain);
    }

    @Override
    public AsciiString asciiDomain() {
        return domain;
    }

    @Override
    public void setDomain(AsciiString domain) {
        this.domain = validateAttributeValue("domain", domain);
    }

    @Override
    public void setDomain(String domain) {
        setDomain(new AsciiString(domain));
    }

    @Override
    @Deprecated
    public String path() {
        return toStringOrNull(path);
    }

    @Override
    public AsciiString asciiPath() {
        return path;
    }

    @Override
    public void setPath(String path) {
        setPath(new AsciiString(path));
    }

    @Override
    public void setPath(AsciiString path) {
        this.path = validateAttributeValue("path", path);
    }

    @Override
    public long maxAge() {
        return maxAge;
    }

    @Override
    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    @Override
    public boolean isHttpOnly() {
        return httpOnly;
    }

    @Override
    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
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
        if (!asciiName().equals(that.asciiName())) {
            return false;
        }

        if (asciiPath() == null) {
            if (that.asciiPath() != null) {
                return false;
            }
        } else if (that.asciiPath() == null) {
            return false;
        } else if (!asciiPath().equals(that.asciiPath())) {
            return false;
        }

        if (asciiDomain() == null) {
            if (that.asciiDomain() != null) {
                return false;
            }
        } else if (that.asciiDomain() == null) {
            return false;
        } else {
            return asciiDomain().contentEqualsIgnoreCase(that.asciiDomain());
        }

        return true;
    }

    @Override
    public int compareTo(Cookie c) {
        int v = asciiName().compareTo(c.asciiName());
        if (v != 0) {
            return v;
        }

        if (asciiPath() == null) {
            if (c.asciiPath() != null) {
                return -1;
            }
        } else if (c.asciiPath() == null) {
            return 1;
        } else {
            v = asciiPath().compareTo(c.asciiPath());
            if (v != 0) {
                return v;
            }
        }

        if (asciiDomain() == null) {
            if (c.asciiDomain() != null) {
                return -1;
            }
        } else if (c.asciiDomain() == null) {
            return 1;
        } else {
            v = asciiDomain().compareToIgnoreCase(c.asciiDomain());
            return v;
        }

        return 0;
    }

    /**
     * Validate a cookie attribute value, throws a {@link IllegalArgumentException} otherwise.
     * Only intended to be used by {@link io.netty.handler.codec.http.DefaultCookie}.
     * @param name attribute name
     * @param value attribute value
     * @return the trimmed, validated attribute value
     * @deprecated CookieUtil is package private, will be removed once old Cookie API is dropped
     */
    @Deprecated
    protected String validateValue(String name, String value) {
        return validateAttributeValue(name, new AsciiString(value)).toString();
    }

    @Override
    public String toString() {
        StringBuilder buf = stringBuilder()
            .append(asciiName())
            .append('=')
            .append(asciiValue());
        if (asciiDomain() != null) {
            buf.append(", domain=")
               .append(asciiDomain());
        }
        if (asciiPath() != null) {
            buf.append(", path=")
               .append(asciiPath());
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
        return buf.toString();
    }
}
