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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits an HTTP query string into a path string and key-value parameter pairs.
 * This decoder is for one time use only.  Create a new instance for each URI:
 * <pre>
 * {@link QueryStringDecoder} decoder = new {@link QueryStringDecoder}("/hello?recipient=world");
 * assert decoder.getPath().equals("/hello");
 * assert decoder.getParameters().get("recipient").equals("world");
 * </pre>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev$, $Date$
 *
 * @see QueryStringEncoder
 *
 * @apiviz.stereotype utility
 * @apiviz.has        org.jboss.netty.handler.codec.http.HttpRequest oneway - - decodes
 */
public class QueryStringDecoder {

    private static final Pattern PARAM_PATTERN = Pattern.compile("([^=]*)=([^&]*)&*");

    private final Charset charset;
    private final String uri;
    private String path;
    private final Map<String, List<String>> params = new HashMap<String, List<String>>();

    /**
     * Creates a new decoder that decodes the specified URI. The decoder will
     * assume that the query string is encoded in UTF-8.
     */
    public QueryStringDecoder(String uri) {
        this(uri, HttpCodecUtil.DEFAULT_CHARSET);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(String uri, Charset charset) {
        if (uri == null) {
            throw new NullPointerException("uri");
        }
        if (charset == null) {
            throw new NullPointerException("charset");
        }

        this.uri = uri;
        this.charset = charset;
    }

    /**
     * @deprecated Use {@link #QueryStringDecoder(String, Charset)} instead.
     */
    @Deprecated
    public QueryStringDecoder(String uri, String charset) {
        this(uri, Charset.forName(charset));
    }

    /**
     * Creates a new decoder that decodes the specified URI. The decoder will
     * assume that the query string is encoded in UTF-8.
     */
    public QueryStringDecoder(URI uri) {
        this(uri, HttpCodecUtil.DEFAULT_CHARSET);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(URI uri, Charset charset){
        if (uri == null) {
            throw new NullPointerException("uri");
        }
        if (charset == null) {
            throw new NullPointerException("charset");
        }

        this.uri = uri.toASCIIString();
        this.charset = charset;
    }

    /**
     * @deprecated Use {@link #QueryStringDecoder(URI, Charset)} instead.
     */
    @Deprecated
    public QueryStringDecoder(URI uri, String charset){
        this(uri, Charset.forName(charset));
    }

    /**
     * Returns the decoded path string of the URI.
     */
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

    /**
     * Returns the decoded key-value parameter pairs of the URI.
     */
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
        int pathEndPos = uri.indexOf('?');
        if (pathEndPos < 0) {
            path = uri;
        } else {
            path = uri.substring(0, pathEndPos);
            decodeParams(uri.substring(pathEndPos + 1));
        }
    }

    private void decodeParams(String s) {
        Matcher m = PARAM_PATTERN.matcher(s);
        int pos = 0;
        while (m.find(pos)) {
            pos = m.end();
            String key = decodeComponent(m.group(1), charset);
            String value = decodeComponent(m.group(2), charset);

            List<String> values = params.get(key);
            if(values == null) {
                values = new ArrayList<String>();
                params.put(key,values);
            }
            values.add(value);
        }
    }

    private static String decodeComponent(String s, Charset charset) {
        if (s == null) {
            return "";
        }

        try {
            return URLDecoder.decode(s, charset.name());
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedCharsetException(charset.name());
        }
    }
}
