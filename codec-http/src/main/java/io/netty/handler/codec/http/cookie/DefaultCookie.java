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

import io.netty.handler.codec.http.cookie.CookieHeaderNames.SameSite;

import static io.netty.handler.codec.http.cookie.CookieUtil.stringBuilder;
import static io.netty.handler.codec.http.cookie.CookieUtil.validateAttributeValue;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link Cookie} implementation.
 */
public class DefaultCookie implements Cookie {

    private final String name;
    private String value;
    private boolean wrap;
    private String domain;
    private String path;
    private long maxAge = UNDEFINED_MAX_AGE;
    private boolean secure;
    private boolean httpOnly;
    private SameSite sameSite;

    /**
     * Creates a new cookie with the specified name and value.
     */
    public DefaultCookie(String name, String value) {
        name = checkNotNull(name, "name").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("empty name");
        }
        this.name = name;
        setValue(value);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public void setValue(String value) {
        this.value = checkNotNull(value, "value");
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
    public String domain() {
        return domain;
    }

    @Override
    public void setDomain(String domain) {
        this.domain = validateAttributeValue("domain", domain);
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public void setPath(String path) {
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
        if (!name().equals(that.name())) {
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
        } else {
            return domain().equalsIgnoreCase(that.domain());
        }

        return true;
    }

    @Override
    public int compareTo(Cookie c) {
        int v = name().compareTo(c.name());
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
        return validateAttributeValue(name, value);
    }

    @Override
    public String toString() {
        StringBuilder buf = stringBuilder()
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
}
