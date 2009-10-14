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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.internal.CaseIgnoringComparator;

/**
 * The default {@link HttpMessage} implementation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class DefaultHttpMessage implements HttpMessage {

    private final HttpVersion version;
    private final Map<String, List<String>> headers = new TreeMap<String, List<String>>(CaseIgnoringComparator.INSTANCE);
    private ChannelBuffer content = ChannelBuffers.EMPTY_BUFFER;

    /**
     * Creates a new instance.
     */
    protected DefaultHttpMessage(final HttpVersion version) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        this.version = version;
    }

    public void addHeader(final String name, final String value) {
        validateHeaderName(name);
        validateHeaderValue(value);
        if (headers.get(name) == null) {
            headers.put(name, new ArrayList<String>(1));
        }
        headers.get(name).add(value);
    }

    public void setHeader(final String name, final String value) {
        validateHeaderName(name);
        validateHeaderValue(value);
        List<String> values = new ArrayList<String>(1);
        values.add(value);
        headers.put(name, values);
    }

    public void setHeader(final String name, final Iterable<String> values) {
        validateHeaderName(name);
        if (values == null) {
            throw new NullPointerException("values");
        }

        int nValues = 0;
        for (String v: values) {
            validateHeaderValue(v);
            nValues ++;
        }

        if (nValues == 0) {
            throw new IllegalArgumentException("values is empty.");
        }

        if (values instanceof List<?>) {
            headers.put(name, (List<String>) values);
        } else {
            List<String> valueList = new LinkedList<String>();
            for (String v: values) {
                valueList.add(v);
            }
            headers.put(name, valueList);
        }
    }

    private static void validateHeaderName(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        for (int i = 0; i < name.length(); i ++) {
            char c = name.charAt(i);
            if (c > 127) {
                throw new IllegalArgumentException(
                        "name contains non-ascii character: " + name);
            }

            // Check prohibited characters.
            switch (c) {
            case '=':  case ',':  case ';': case ' ': case ':':
            case '\t': case '\r': case '\n': case '\f':
            case 0x0b: // Vertical tab
                throw new IllegalArgumentException(
                        "name contains one of the following prohibited characters: " +
                        "=,;: \\t\\r\\n\\v\\f: " + name);
            }
        }
    }

    private static void validateHeaderValue(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        // 0 - the previous character was neither CR nor LF
        // 1 - the previous character was CR
        // 2 - the previous character was LF
        int state = 0;

        for (int i = 0; i < value.length(); i ++) {
            char c = value.charAt(i);

            // Check the absolutely prohibited characters.
            switch (c) {
            case '\f':
                throw new IllegalArgumentException(
                        "value contains a prohibited character '\\f': " + value);
            case 0x0b: // Vertical tab
                throw new IllegalArgumentException(
                        "value contains a prohibited character '\\v': " + value);
            }

            // Check the CRLF (HT | SP) pattern
            switch (state) {
            case 0:
                switch (c) {
                case '\r':
                    state = 1;
                    break;
                case '\n':
                    state = 2;
                    break;
                }
                break;
            case 1:
                switch (c) {
                case '\n':
                    state = 2;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Only '\\n' is allowed after '\\r': " + value);
                }
                break;
            case 2:
                switch (c) {
                case ' ': case '\t':
                    state = 0;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Only ' ' and '\\t' are allowed after '\\n': " + value);
                }
            }
        }

        if (state != 0) {
            throw new IllegalArgumentException(
                    "value must not end with '\\r' or '\\n':" + value);
        }
    }

    public void removeHeader(final String name) {
        headers.remove(name);
    }

    public long getContentLength() {
        return getContentLength(0);
    }

    public long getContentLength(long defaultValue) {
        List<String> contentLength = headers.get(HttpHeaders.Names.CONTENT_LENGTH);
        if (contentLength != null && contentLength.size() > 0) {
            return Long.parseLong(contentLength.get(0));
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

    public boolean isKeepAlive() {
        if (HttpHeaders.Values.CLOSE.equalsIgnoreCase(getHeader(HttpHeaders.Names.CONNECTION))) {
            return false;
        }

        if (getProtocolVersion().equals(HttpVersion.HTTP_1_0) &&
            !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(getHeader(HttpHeaders.Names.CONNECTION))) {
            return false;
        }
        return true;
    }

    public void clearHeaders() {
        headers.clear();
    }

    public void setContent(ChannelBuffer content) {
        if (content == null) {
            content = ChannelBuffers.EMPTY_BUFFER;
        }
        this.content = content;
    }

    public String getHeader(final String name) {
        List<String> header = headers.get(name);
        return header != null && header.size() > 0 ? headers.get(name).get(0) : null;
    }

    public List<String> getHeaders(final String name) {
        List<String> values = headers.get(name);
        if (values == null) {
            return Collections.emptyList();
        } else {
            return values;
        }
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
}
