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
package io.netty.handler.codec.http.router;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCounted;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MethodRouted<T> implements ReferenceCounted {
    private final T                         target;
    private final boolean                   notFound;
    private final HttpRequest               request;
    private final String                    path;
    private final Map<String, String>       pathParams;
    private final Map<String, List<String>> queryParams;

    private final ReferenceCounted requestAsReferenceCounted;

    //--------------------------------------------------------------------------

    public MethodRouted(
        T                         target,
        boolean                   notFound,
        HttpRequest               request,
        String                    path,
        Map<String, String>       pathParams,
        Map<String, List<String>> queryParams
    ) {
        this.target      = target;
        this.notFound    = notFound;
        this.request     = request;
        this.path        = path;
        this.pathParams  = pathParams;
        this.queryParams = queryParams;

        requestAsReferenceCounted = (request instanceof ReferenceCounted)? (ReferenceCounted) request : null;
    }

    public T target() {
        return target;
    }

    public boolean notFound() {
        return notFound;
    }

    public HttpRequest request() {
        return request;
    }

    public String path() {
        return path;
    }

    public Map<String, String> pathParams() {
        return pathParams;
    }

    public Map<String, List<String>> queryParams() {
        return queryParams;
    }

    //--------------------------------------------------------------------------
    // Utilities to get params.

    /**
     * @return The first query param, or null
     */
    public String queryParam(String name) {
        List<String> values = queryParams.get(name);
        return (values == null)? null : values.get(0);
    }

    /** @return Uses path param first, then falls back to the first query param, or null */
    public String param(String name) {
        String pathValue = pathParams.get(name);
        return (pathValue == null)? queryParam(name) : pathValue;
    }

    /**
     * Both path param and query params are returned.
     * Empty list is returned if there are no such params.
     */
    public List<String> params(String name) {
        List<String> values = queryParams.get(name);
        if (values == null) { values = new ArrayList<String>(); }

        String value = pathParams.get(name);
        if (value != null) { values.add(value); }

        return values;
    }

    //--------------------------------------------------------------------------

    @Override
    public int refCnt() {
        return (requestAsReferenceCounted == null)? 0 : requestAsReferenceCounted.refCnt();
    }

    @Override
    public boolean release() {
        return (requestAsReferenceCounted == null)? true : requestAsReferenceCounted.release();
    }

    @Override
    public boolean release(int arg) {
        return (requestAsReferenceCounted == null)? true : requestAsReferenceCounted.release(arg);
    }

    @Override
    public ReferenceCounted retain() {
        if (requestAsReferenceCounted != null) { requestAsReferenceCounted.retain(); }
        return this;
    }

    @Override
    public ReferenceCounted retain(int arg) {
        if (requestAsReferenceCounted != null) { requestAsReferenceCounted.retain(arg); }
        return this;
    }
}
