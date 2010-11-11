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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * @author <a href="http://tsunanet.net/">Benoit Sigoure</a>
 * @version $Rev: 2302 $, $Date: 2010-06-14 20:07:44 +0900 (Mon, 14 Jun 2010) $
 *
 * @see QueryStringEncoder
 *
 * @apiviz.stereotype utility
 * @apiviz.has        org.jboss.netty.handler.codec.http.HttpRequest oneway - - decodes
 */
public class QueryStringDecoder {

    private final Charset charset;
    private final String uri;
    private String path;
    private Map<String, List<String>> params;

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
        if (path == null) {
            int pathEndPos = uri.indexOf('?');
            if (pathEndPos < 0) {
                path = uri;
            }
            else {
                return path = uri.substring(0, pathEndPos);
            }
        }
        return path;
    }

    /**
     * Returns the decoded key-value parameter pairs of the URI.
     */
    public Map<String, List<String>> getParameters() {
        if (params == null) {
            int pathLength = getPath().length();
            if (uri.length() == pathLength) {
                return Collections.emptyMap();
            }
            params = decodeParams(uri.substring(pathLength + 1));
        }
        return params;
    }

    private Map<String, List<String>> decodeParams(String s) {
        Map<String, List<String>> params = new LinkedHashMap<String, List<String>>();
        String name = null;
        int pos = 0; // Beginning of the unprocessed region
        int i;       // End of the unprocessed region
        char c = 0;  // Current character
        for (i = 0; i < s.length(); i++) {
            c = s.charAt(i);
            if (c == '=' && name == null) {
                if (pos != i) {
                    name = decodeComponent(s.substring(pos, i), charset);
                }
                pos = i + 1;
            } else if (c == '&') {
                if (name == null && pos != i) {
                    // We haven't seen an `=' so far but moved forward.
                    // Must be a param of the form '&a&' so add it with
                    // an empty value.
                    addParam(params, decodeComponent(s.substring(pos, i), charset), "");
                } else if (name != null) {
                    addParam(params, name, decodeComponent(s.substring(pos, i), charset));
                    name = null;
                }
                pos = i + 1;
            }
        }

        if (pos != i) {  // Are there characters we haven't dealt with?
            if (name == null) {     // Yes and we haven't seen any `='.
                addParam(params, decodeComponent(s.substring(pos, i), charset), "");
            } else {                // Yes and this must be the last value.
                addParam(params, name, decodeComponent(s.substring(pos, i), charset));
            }
        } else if (name != null) {  // Have we seen a name without value?
            addParam(params, name, "");
        }

        return params;
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

    private static void addParam(Map<String, List<String>> params, String name, String value) {
        List<String> values = params.get(name);
        if (values == null) {
            values = new ArrayList<String>(1);  // Often there's only 1 value.
            params.put(name, values);
        }
        values.add(value);
    }
}
