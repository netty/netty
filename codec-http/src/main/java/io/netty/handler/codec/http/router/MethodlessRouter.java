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

/**
 * Router that contains information about route matching orders, but doesn't
 * contain information about HTTP request methods.
 *
 * Routes are devided into 3 sections: "first", "last", and "other".
 * Routes in "first" are matched first, then in "other", then in "last".
 */
class MethodlessRouter<T> {
    private final OrderlessRouter<T> first = new OrderlessRouter<T>();
    private final OrderlessRouter<T> other = new OrderlessRouter<T>();
    private final OrderlessRouter<T> last  = new OrderlessRouter<T>();

    //--------------------------------------------------------------------------

    public OrderlessRouter<T> first() {
        return first;
    }

    public OrderlessRouter<T> other() {
        return other;
    }

    public OrderlessRouter<T> last() {
        return last;
    }

    public int size() {
        return first.routes().size() + other.routes().size() + last.routes().size();
    }

    //--------------------------------------------------------------------------

    /**
     * Adds route to the "first" section.
     *
     * A path can only point to one target. This method does nothing if the path
     * has already been added.
     */
    public MethodlessRouter<T> addRouteFirst(String path, T target) {
        first.addRoute(path, target);
        return this;
    }

    /**
     * Adds route to the "other" section.
     *
     * A path can only point to one target. This method does nothing if the path
     * has already been added.
     */
    public MethodlessRouter<T> addRoute(String path, T target) {
        other.addRoute(path, target);
        return this;
    }

    /**
     * Adds route to the "last" section.
     *
     * A path can only point to one target. This method does nothing if the path
     * has already been added.
     */
    public MethodlessRouter<T> addRouteLast(String path, T target) {
        last.addRoute(path, target);
        return this;
    }

    //--------------------------------------------------------------------------

    /** Removes the route specified by the path. */
    public void removePath(String path) {
        first.removePath(path);
        other.removePath(path);
        last .removePath(path);
    }

    /** Removes all routes leading to the target. */
    public void removeTarget(T target) {
        first.removeTarget(target);
        other.removeTarget(target);
        last .removeTarget(target);
    }

    //--------------------------------------------------------------------------

    /** @return {@code null} if no match; note: {@code queryParams} is not set in {@link RouteResult} */
    public RouteResult<T> route(String path) {
        return route(Path.removeSlashesAtBothEnds(path).split("/"));
    }

    /** @return {@code null} if no match; note: {@code queryParams} is not set in {@link RouteResult} */
    public RouteResult<T> route(String[] requestPathTokens) {
        RouteResult<T> ret = first.route(requestPathTokens);
        if (ret != null) {
            return ret;
        }

        ret = other.route(requestPathTokens);
        if (ret != null) {
            return ret;
        }

        ret = last.route(requestPathTokens);
        if (ret != null) {
            return ret;
        }

        return null;
    }

    /** Checks if there's any matching route. */
    public boolean anyMatched(String[] requestPathTokens) {
        return first.anyMatched(requestPathTokens) ||
               other.anyMatched(requestPathTokens) ||
               last.anyMatched(requestPathTokens);
    }

    /**
     * Given a target and params, this method tries to do the reverse routing
     * and returns the path.
     *
     * The params are put to placeholders in the path.
     * The params can be a map of {@code placeholder name -> value}
     * or ordered values. If a param doesn't have a placeholder, it will be put
     * to the query part of the path.
     *
     * @return {@code null} if there's no match
     */
    public String path(T target, Object... params) {
        String ret = first.path(target, params);
        if (ret != null) {
            return ret;
        }

        ret = other.path(target, params);
        if (ret != null) {
            return ret;
        }

        return last.path(target, params);
    }
}
