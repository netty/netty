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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 *
 * @apiviz.stereotype utility
 */
public class QueryStringEncoder {

    private final String charset;
    private final String uri;
    private final List<Param> params = new ArrayList<Param>();

    public QueryStringEncoder(String uri) {
        this(uri, QueryStringDecoder.DEFAULT_CHARSET);
    }

    public QueryStringEncoder(String uri, String charset) {
        if (uri == null) {
            throw new NullPointerException("uri");
        }
        if (charset == null) {
            throw new NullPointerException("charset");
        }

        this.uri = uri;
        this.charset = charset;
    }

    public void addParam(String name, String value) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (value == null) {
            throw new NullPointerException("value");
        }
        params.add(new Param(name, value));
    }

    public URI toUri() throws URISyntaxException {
        return new URI(toString());
    }

    @Override
    public String toString() {
        if (params.isEmpty()) {
            return uri;
        } else {
            StringBuilder sb = new StringBuilder(uri).append("?");
            for (int i = 0; i < params.size(); i++) {
                Param param = params.get(i);
                sb.append(encodeComponent(param.name, charset));
                sb.append("=");
                sb.append(encodeComponent(param.value, charset));
                if(i != params.size() - 1) {
                    sb.append("&");
                }
            }
            return sb.toString();
        }
    }

    static String encodeComponent(String s, String charset) {
        try {
            return URLEncoder.encode(s, charset).replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedCharsetException(charset);
        }
    }

    private static final class Param {

        final String name;
        final String value;

        Param(String name, String value) {
            this.value = value;
            this.name = name;
        }
    }
}
