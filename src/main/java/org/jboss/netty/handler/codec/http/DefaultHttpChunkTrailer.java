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
 * The default {@link HttpChunkTrailer} implementation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
public class DefaultHttpChunkTrailer implements HttpChunkTrailer {

    // FIXME Lots of code duplication with DefaultHttpMessage
    private final Map<String, List<String>> headers = new TreeMap<String, List<String>>(CaseIgnoringComparator.INSTANCE);

    public boolean isLast() {
        return true;
    }

    public void addHeader(final String name, final String value) {
        validateHeaderName(name);
        HttpCodecUtil.validateHeaderValue(value);
        if (headers.get(name) == null) {
            headers.put(name, new ArrayList<String>(1));
        }
        headers.get(name).add(value);
    }

    public void setHeader(final String name, final String value) {
        validateHeaderName(name);
        HttpCodecUtil.validateHeaderValue(value);
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
            HttpCodecUtil.validateHeaderValue(v);
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
        HttpCodecUtil.validateHeaderName(name);
        if (name.equalsIgnoreCase(HttpHeaders.Names.CONTENT_LENGTH) ||
            name.equalsIgnoreCase(HttpHeaders.Names.TRANSFER_ENCODING) ||
            name.equalsIgnoreCase(HttpHeaders.Names.TRAILER)) {
            throw new IllegalArgumentException(
                    "prohibited trailing header: " + name);
        }
    }

    public void removeHeader(final String name) {
        headers.remove(name);
    }

    public void clearHeaders() {
        headers.clear();
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

    public ChannelBuffer getContent() {
        return ChannelBuffers.EMPTY_BUFFER;
    }
}
