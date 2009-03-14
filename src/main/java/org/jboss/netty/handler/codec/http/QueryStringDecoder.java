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
import java.net.URLDecoder;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 *
 * @apiviz.stereotype utility
 */
public class QueryStringDecoder {

    static final String DEFAULT_CHARSET = "UTF-8";

    private final String charset;
    private final String uri;
    private String path;
    private final Map<String, List<String>> params = new HashMap<String, List<String>>();

    public QueryStringDecoder(String uri) {
        this(uri, DEFAULT_CHARSET);
    }

    public QueryStringDecoder(String uri, String charset) {
        if (uri == null) {
            throw new NullPointerException("uri");
        }
        if (charset == null) {
            throw new NullPointerException("charset");
        }

        this.uri = uri;
        this.charset = charset;
    }

    public QueryStringDecoder(URI uri) {
        this(uri, DEFAULT_CHARSET);
    }

    public QueryStringDecoder(URI uri, String charset){
        if (uri == null) {
            throw new NullPointerException("uri");
        }
        if (charset == null) {
            throw new NullPointerException("charset");
        }

        this.uri = uri.toASCIIString();
        this.charset = charset;
    }

    public String getPath() {
        //decode lazily
        if(path == null) {
            if(uri.contains("?")) {
                decode();
            }
            else {
                path = uri;
            }
        }
        return path;
    }

    public Map<String, List<String>> getParameters() {
        if(path == null){
            if(uri.contains("?")) {
                decode();
            }
            else {
                path = uri;
            }
        }
        return params;
    }

    private void decode() {
        String[] split = uri.split("\\?", 2);
        path = split[0];
        decodeParams(split[1]);
    }

    private void decodeParams(String s) {
        String[] params = s.split("&");
        for (String param : params) {
            String[] split = param.split("=");
            String key = decodeComponent(split[0], charset);
            List<String> values = this.params.get(key);
            if(values == null) {
                values = new ArrayList<String>();
                this.params.put(key,values);
            }
            if (split.length > 1) {
                values.add(decodeComponent(split[1], charset));
            } else {
                values.add("");
            }
        }
    }

    private static String decodeComponent(String s, String charset) {
        try {
            return URLDecoder.decode(s, charset);
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedCharsetException(charset);
        }
    }
}
