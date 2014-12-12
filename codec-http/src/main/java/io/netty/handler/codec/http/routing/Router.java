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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public abstract class Router<M, T, RouterLike extends Router<M, T, RouterLike>> {
    protected abstract RouterLike getThis();

    protected abstract M CONNECT();
    protected abstract M DELETE();
    protected abstract M GET();
    protected abstract M HEAD();
    protected abstract M OPTIONS();
    protected abstract M PATCH();
    protected abstract M POST();
    protected abstract M PUT();
    protected abstract M TRACE();

    //--------------------------------------------------------------------------

    protected final Map<M, MethodlessRouter<T>> routers =
        new HashMap<M, MethodlessRouter<T>>();

    protected final MethodlessRouter<T> anyMethodRouter =
        new MethodlessRouter<T>();

    protected T notFound;

    //--------------------------------------------------------------------------

    public RouterLike pattern(M method, String path, T target) {
        getMethodlessRouter(method).pattern(path, target);
        return getThis();
    }

    public RouterLike patternFirst(M method, String path, T target) {
        getMethodlessRouter(method).patternFirst(path, target);
        return getThis();
    }

    public RouterLike patternLast(M method, String path, T target) {
        getMethodlessRouter(method).patternLast(path, target);
        return getThis();
    }

    public RouterLike notFound(T target) {
        this.notFound = target;
        return getThis();
    }

    private MethodlessRouter<T> getMethodlessRouter(M method) {
        if (method == null) { return anyMethodRouter; }

        MethodlessRouter<T> r = routers.get(method);
        if (r == null) {
            r = new MethodlessRouter<T>();
            routers.put(method, r);
        }

        return r;
    }

    //--------------------------------------------------------------------------

    public void removeTarget(T target) {
        for (MethodlessRouter<T> r : routers.values()) { r.removeTarget(target); }
        anyMethodRouter.removeTarget(target);
    }

    public void removePath(String path) {
        for (MethodlessRouter<T> r : routers.values()) { r.removePath(path); }
        anyMethodRouter.removePath(path);
    }

    //--------------------------------------------------------------------------

    public Routed<T> route(M method, String path) {
        MethodlessRouter<T> router = routers.get(method);
        if (router == null) { router = anyMethodRouter; }

        Routed<T> ret = router.route(path);
        if (ret != null) { return ret; }

        if (router != anyMethodRouter) {
            ret = anyMethodRouter.route(path);
            if (ret != null) { return ret; }
        }

        if (notFound != null) { return new Routed<T>(notFound, true, Collections.<String, String>emptyMap()); }

        return null;
    }

    //--------------------------------------------------------------------------
    // Reverse routing.

    public String path(M method, T target, Object... params) {
        MethodlessRouter<T> router = (method == null)? anyMethodRouter : routers.get(method);
        if (router == null) { router = anyMethodRouter; }

        String ret = router.path(target, params);
        if (ret != null) { return ret; }

        return (router == anyMethodRouter)? null : anyMethodRouter.path(target, params);
    }

    public String path(T target, Object... params) {
        Collection<MethodlessRouter<T>> rs = routers.values();
        for (MethodlessRouter<T> r : rs) {
            String ret = r.path(target, params);
            if (ret != null) { return ret; }
        }
        return anyMethodRouter.path(target, params);
    }

    //--------------------------------------------------------------------------

    /** Visualizes the routes. */
    @Override
    public String toString() {
        // Step 1/2: Dump routers and anyMethodRouter in order
        List<String> methods = new ArrayList<String>();
        List<String> paths   = new ArrayList<String>();
        List<String> targets = new ArrayList<String>();

        // For router
        for (Entry<M, MethodlessRouter<T>> e : routers.entrySet()) {
            M                   method = e.getKey();
            MethodlessRouter<T> router = e.getValue();
            listPatterns(method.toString(), router.first().patterns(), methods, paths, targets);
            listPatterns(method.toString(), router.other().patterns(), methods, paths, targets);
            listPatterns(method.toString(), router.last ().patterns(), methods, paths, targets);
        }

        // For anyMethodRouter
        listPatterns("*", anyMethodRouter.first().patterns(), methods, paths, targets);
        listPatterns("*", anyMethodRouter.other().patterns(), methods, paths, targets);
        listPatterns("*", anyMethodRouter.last ().patterns(), methods, paths, targets);

        // For notFound
        if (notFound != null) {
            methods.add("*");
            paths  .add("*");
            targets.add(targetToString(notFound));
        }

        // Step 2/2: Format the List into aligned columns: <method> <path> <target>
        int    maxLengthMethod = maxLength(methods);
        int    maxLengthPath   = maxLength(paths);
        String format          = "%-" + maxLengthMethod + "s  %-" + maxLengthPath + "s  %s\n";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < methods.size(); i++) {
            String method = methods.get(i);
            String path   = paths  .get(i);
            String target = targets.get(i);
            b.append(String.format(format, method, path, target));
        }
        return b.toString();
    }

    /** Helper for toString */
    private void listPatterns(
            String method, List<Pattern<T>> patterns,
            List<String> methods, List<String> paths, List<String> targets) {
        for (Pattern<T> pattern : patterns) {
            methods.add(method);
            paths  .add("/" + pattern.path());
            targets.add(targetToString(pattern.target()));
        }
    }

    /** Helper for toString */
    private int maxLength(List<String> coll) {
        int max = 0;
        for (String e : coll) {
            int length = e.length();
            if (length > max) max = length;
        }
        return max;
    }

    /**
     * Helper for toString; for example, returns
     * "io.netty.example.http.router.HttpRouterServerHandler" instead of
     * "class io.netty.example.http.router.HttpRouterServerHandler"
     */
    private String targetToString(Object target) {
        if (target instanceof Class) {
            String className = ((Class<?>) target).getName();
            return className;
        } else {
            return target.toString();
        }
    }

    //--------------------------------------------------------------------------

    public RouterLike CONNECT(String path, T target) {
        return pattern(CONNECT(), path, target);
    }

    public RouterLike DELETE(String path, T target) {
        return pattern(DELETE(), path, target);
    }

    public RouterLike GET(String path, T target) {
        return pattern(GET(), path, target);
    }

    public RouterLike HEAD(String path, T target) {
        return pattern(HEAD(), path, target);
    }

    public RouterLike OPTIONS(String path, T target) {
        return pattern(OPTIONS(), path, target);
    }

    public RouterLike PATCH(String path, T target) {
        return pattern(PATCH(), path, target);
    }

    public RouterLike POST(String path, T target) {
        return pattern(POST(), path, target);
    }

    public RouterLike PUT(String path, T target) {
        return pattern(PUT(), path, target);
    }

    public RouterLike TRACE(String path, T target) {
        return pattern(TRACE(), path, target);
    }

    public RouterLike ANY(String path, T target) {
        return pattern(null, path, target);
    }

    //--------------------------------------------------------------------------

    public RouterLike CONNECT_FIRST(String path, T target) {
        return patternFirst(CONNECT(), path, target);
    }

    public RouterLike DELETE_FIRST(String path, T target) {
        return patternFirst(DELETE(), path, target);
    }

    public RouterLike GET_FIRST(String path, T target) {
        return patternFirst(GET(), path, target);
    }

    public RouterLike HEAD_FIRST(String path, T target) {
        return patternFirst(HEAD(), path, target);
    }

    public RouterLike OPTIONS_FIRST(String path, T target) {
        return patternFirst(OPTIONS(), path, target);
    }

    public RouterLike PATCH_FIRST(String path, T target) {
        return patternFirst(PATCH(), path, target);
    }

    public RouterLike POST_FIRST(String path, T target) {
        return patternFirst(POST(), path, target);
    }

    public RouterLike PUT_FIRST(String path, T target) {
        return patternFirst(PUT(), path, target);
    }

    public RouterLike TRACE_FIRST(String path, T target) {
        return patternFirst(TRACE(), path, target);
    }

    public RouterLike ANY_FIRST(String path, T target) {
        return patternFirst(null, path, target);
    }

    //--------------------------------------------------------------------------

    public RouterLike CONNECT_LAST(String path, T target) {
        return patternLast(CONNECT(), path, target);
    }

    public RouterLike DELETE_LAST(String path, T target) {
        return patternLast(DELETE(), path, target);
    }

    public RouterLike GET_LAST(String path, T target) {
        return patternLast(GET(), path, target);
    }

    public RouterLike HEAD_LAST(String path, T target) {
        return patternLast(HEAD(), path, target);
    }

    public RouterLike OPTIONS_LAST(String path, T target) {
        return patternLast(OPTIONS(), path, target);
    }

    public RouterLike PATCH_LAST(String path, T target) {
        return patternLast(PATCH(), path, target);
    }

    public RouterLike POST_LAST(String path, T target) {
        return patternLast(POST(), path, target);
    }

    public RouterLike PUT_LAST(String path, T target) {
        return patternLast(PUT(), path, target);
    }

    public RouterLike TRACE_LAST(String path, T target) {
        return patternLast(TRACE(), path, target);
    }

    public RouterLike ANY_LAST(String path, T target) {
        return patternLast(null, path, target);
    }
}
