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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Collection;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * a default Http Message which holds the headers and body.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class DefaultHttpMessage implements HttpMessage {
    private final static Comparator<String> caseIgnoringComparator = new CaseIgnoringComparator();

    private final HttpVersion version;
    private final Map<String, List<String>> headers = new TreeMap<String, List<String>>(caseIgnoringComparator);
    private final Map<String, HttpCookie> cookies = new TreeMap<String, HttpCookie>(caseIgnoringComparator);
    private ChannelBuffer content;

    protected DefaultHttpMessage(final HttpVersion version) {
        this.version = version;
    }

    public void addHeader(final String name, final String value) {
        if (value == null) {
            throw new NullPointerException("value is null");
        }
        if (headers.get(name) == null) {
            headers.put(name, new ArrayList<String>());
        }
        headers.get(name).add(value);
    }

    public void setHeader(final String name, final String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        List<String> values = new ArrayList<String>(1);
        values.add(value);
        headers.put(name, values);
    }

    public void setHeader(final String name, final List<String> values) {
        if (values == null || values.size() == 0) {
            throw new NullPointerException("no values present");
        }
        headers.put(name, values);
    }

    public void removeHeader(final String name) {
        headers.remove(name);
    }

    public int getContentLength() {
        return getContentLength(0);
    }

    public int getContentLength(int defaultValue) {
        List<String> contentLength = headers.get(HttpHeaders.Names.CONTENT_LENGTH);
        if (contentLength != null && contentLength.size() > 0) {
            return Integer.parseInt(contentLength.get(0));
        }
        return defaultValue;
    }

    public boolean isChunked() {
        List<String> chunked = headers.get(HttpHeaders.Names.TRANSFER_ENCODING);
        if (chunked == null || chunked.isEmpty()) {
            return false;
        }

        for (String v: chunked) {
            if (v.equalsIgnoreCase(HttpHeaders.Values.CHUNKED)) {
                return true;
            }
        }
        return false;
    }

    public void clearHeaders() {
        headers.clear();
    }

    public void setContent(final ChannelBuffer content) {
        this.content = content;
    }

    public String getHeader(final String name) {
        List<String> header = headers.get(name);
        return header != null && header.size() > 0 ? headers.get(name).get(0) : null;
    }

    public List<String> getHeaders(final String name) {
        return headers.get(name);
    }

    public boolean containsHeader(final String name) {
        return headers.containsKey(name);
    }

    public Set<String> getHeaderNames() {
        return headers.keySet();
    }

    public HttpVersion getProtocolVersion() {
        return version;
    }

    public ChannelBuffer getContent() {
        return content;
    }

    public void addCookie(HttpCookie cookie) {
        cookies.put(cookie.getName(), cookie);
    }

    public HttpCookie getCookie(String name) {
        return cookies.get(name);
    }

    public Collection<HttpCookie> getCookies() {
        return cookies.values();
    }
    public Collection<String> getCookieNames() {
        return cookies.keySet();
    }

    private static final class CaseIgnoringComparator
            implements Comparator<String>, Serializable {

        private static final long serialVersionUID = 4582133183775373862L;

        CaseIgnoringComparator() {
            super();
        }

        public int compare(String o1, String o2) {
            return o1.compareToIgnoreCase(o2);
        }
    }
}
