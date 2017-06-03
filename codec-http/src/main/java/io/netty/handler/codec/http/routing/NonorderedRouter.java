/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http.routing;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NonorderedRouter<T> {
    protected final List<Pattern<T>> patterns = new ArrayList<Pattern<T>>();

    // Reverse index to create reverse routes fast
    protected final Map<T, List<Pattern<T>>> reverse = new HashMap<T, List<Pattern<T>>>();

    protected T notFound;

    //--------------------------------------------------------------------------

    public List<Pattern<T>> patterns() { return patterns; }

    public NonorderedRouter<T> pattern(String path, T target) {
        final Pattern<T> pattern = new Pattern<T>(path, target);
        patterns.add(pattern);

        List<Pattern<T>> patterns = reverse.get(target);
        if (patterns == null) {
            patterns = new ArrayList<Pattern<T>>();
            patterns.add(pattern);
            reverse.put(target, patterns);
        } else {
            patterns.add(pattern);
        }

        return this;
    }

    public NonorderedRouter<T> notFound(T target) {
        this.notFound = target;
        return this;
    }

    //--------------------------------------------------------------------------

    public void removeTarget(T target) {
        Iterator<Pattern<T>> it = patterns.iterator();
        while (it.hasNext()) {
            Pattern<T> pattern = it.next();
            if (pattern.target().equals(target)) { it.remove(); }
        }

        reverse.remove(target);
    }

    public void removePath(String path) {
        String normalizedPath = Pattern.removeSlashAtBothEnds(path);

        removePatternByPath(patterns, normalizedPath);

        for (Map.Entry<T, List<Pattern<T>>> entry : reverse.entrySet()) {
            List<Pattern<T>> patterns = entry.getValue();
            removePatternByPath(patterns, normalizedPath);
        }
    }

    private void removePatternByPath(List<Pattern<T>> patterns, String path) {
        Iterator<Pattern<T>> it = patterns.iterator();
        while (it.hasNext()) {
            Pattern<T> pattern = it.next();
            if (pattern.path().equals(path)) { it.remove(); }
        }
    }

    //--------------------------------------------------------------------------

    public Routed<T> route(String path) {
        final String[]            tokens  = Pattern.removeSlashAtBothEnds(path).split("/");
        final Map<String, String> params  = new HashMap<String, String>();
        boolean                   matched = true;

        for (final Pattern<T> pattern : patterns) {
            final String[] currTokens = pattern.tokens();
            final T        target     = pattern.target();

            matched = true;
            params.clear();

            if (tokens.length == currTokens.length) {
                for (int i = 0; i < currTokens.length; i++) {
                    final String token     = tokens[i];
                    final String currToken = currTokens[i];

                    if (currToken.length() > 0 && currToken.charAt(0) == ':') {
                        params.put(currToken.substring(1), token);
                    } else if (!currToken.equals(token)) {
                        matched = false;
                        break;
                    }
                }
            } else if (currTokens.length > 0 &&
                       currTokens[currTokens.length - 1].equals(":*") && tokens.length >= currTokens.length)
            {
                for (int i = 0; i < currTokens.length - 1; i++) {
                    final String token     = tokens[i];
                    final String currToken = currTokens[i];

                    if (currToken.length() > 0 && currToken.charAt(0) == ':') {
                        params.put(currToken.substring(1), token);
                    } else if (!token.equals(token)) {
                        matched = false;
                        break;
                    }
                }

                if (matched) {
                    final StringBuilder b = new StringBuilder(tokens[currTokens.length - 1]);
                    for (int i = currTokens.length; i < tokens.length; i++) {
                        b.append('/');
                        b.append(tokens[i]);
                    }
                    params.put("*", b.toString());
                }
            } else {
                matched = false;
            }

            if (matched) { return new Routed<T>(target, false, params); }
        }

        if (notFound != null) {
            params.clear();
            return new Routed<T>(notFound, true, params);
        }

        return null;
    }

    //--------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public String path(T target, Object... params) {
        if (params.length == 0) {
            return path(target, Collections.emptyMap());
        }

        if (params.length == 1 && params[0] instanceof Map<?, ?>) {
            return pathMap(target, (Map<Object, Object>) params[0]);
        }

        if (params.length % 2 == 1) {
            throw new RuntimeException("Missing value for param: " + params[params.length - 1]);
        }

        final Map<Object, Object> map = new HashMap<Object, Object>();
        for (int i = 0; i < params.length; i += 2) {
            final String key   = params[i].toString();
            final String value = params[i + 1].toString();
            map.put(key, value);
        }
        return pathMap(target, map);
    }

    private String pathMap(T target, Map<Object, Object> params) {
        final List<Pattern<T>> patterns = (target instanceof Class<?>) ?
                getPatternsByTargetClass((Class<?>) target) : reverse.get(target);
                if (patterns == null) { return null; }

                try {
                    // The best one is the one with minimum number of params in the query
                    String bestCandidate  = null;
                    int    minQueryParams = Integer.MAX_VALUE;

                    boolean           matched  = true;
                    final Set<String> usedKeys = new HashSet<String>();

                    for (final Pattern<T> pattern : patterns) {
                        matched = true;
                        usedKeys.clear();

                        final StringBuilder b = new StringBuilder();

                        for (final String token : pattern.tokens()) {
                            b.append('/');

                            if (token.length() > 0 && token.charAt(0) == ':') {
                                final String key   = token.substring(1);
                                final Object value = params.get(key);
                                if (value == null) {
                                    matched = false;
                                    break;
                                }

                                usedKeys.add(key);
                                b.append(value.toString());
                            } else {
                                b.append(token);
                            }
                        }

                        if (matched) {
                            final int numQueryParams = params.size() - usedKeys.size();
                            if (numQueryParams < minQueryParams) {
                                if (numQueryParams > 0) {
                                    boolean firstQueryParam = true;

                                    for (final Map.Entry<Object, Object> entry : params.entrySet()) {
                                        final String key = entry.getKey().toString();
                                        if (!usedKeys.contains(key)) {
                                            if (firstQueryParam) {
                                                b.append('?');
                                                firstQueryParam = false;
                                            } else {
                                                b.append('&');
                                            }

                                            final String value = entry.getValue().toString();

                                            // May throw UnsupportedEncodingException
                                            b.append(URLEncoder.encode(key, "UTF-8"));

                                            b.append('=');

                                            // May throw UnsupportedEncodingException
                                            b.append(URLEncoder.encode(value, "UTF-8"));
                                        }
                                    }
                                }

                                bestCandidate  = b.toString();
                                minQueryParams = numQueryParams;
                            }
                        }
                    }

                    return bestCandidate;
                } catch (UnsupportedEncodingException e) {
                    return null;
                }
    }

    /** @return null if there's no match */
    private List<Pattern<T>> getPatternsByTargetClass(Class<?> klass) {
        List<Pattern<T>> ret = null;
        for (Map.Entry<T, List<Pattern<T>>> entry : reverse.entrySet()) {
            T       key     = entry.getKey();
            boolean matched = false;

            if (key == klass) {
                matched = true;
            } else if (!(key instanceof Class<?>)) {
                Class<?> keyClass = key.getClass();
                if (klass.isAssignableFrom(keyClass)) { matched = true; }
            }

            if (matched) {
                if (ret == null) { ret = new ArrayList<Pattern<T>>(); }
                ret.addAll(entry.getValue());
            }
        }

        return ret;
    }
}
