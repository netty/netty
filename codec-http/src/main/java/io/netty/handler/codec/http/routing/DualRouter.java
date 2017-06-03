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

/** Similar to Router, but the target can be both class or instances of the class. */
public abstract class DualRouter<M, T, RouterLike extends DualRouter<M, T, RouterLike>> {
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

    class ObjectRouter extends Router<M, Object, ObjectRouter> {
        @Override public String toString() { return super.toString(); }

        @Override protected DualRouter<M, T, RouterLike>.ObjectRouter getThis() { return this; }

        @Override protected M CONNECT() { return DualRouter.this.CONNECT(); }
        @Override protected M DELETE()  { return DualRouter.this.DELETE(); }
        @Override protected M GET()     { return DualRouter.this.GET(); }
        @Override protected M HEAD()    { return DualRouter.this.HEAD(); }
        @Override protected M OPTIONS() { return DualRouter.this.OPTIONS(); }
        @Override protected M PATCH()   { return DualRouter.this.PATCH(); }
        @Override protected M POST()    { return DualRouter.this.POST(); }
        @Override protected M PUT()     { return DualRouter.this.PUT(); }
        @Override protected M TRACE()   { return DualRouter.this.TRACE(); }
    }

    protected final ObjectRouter router = new ObjectRouter();

    @Override public String toString() { return router.toString(); }

    //--------------------------------------------------------------------------

    public RouterLike pattern(M method, String path, T target) {
        router.pattern(method, path, target);
        return getThis();
    }

    public RouterLike patternFirst(M method, String path, T target) {
        router.patternFirst(method, path, target);
        return getThis();
    }

    public RouterLike patternLast(M method, String path, T target) {
        router.patternLast(method, path, target);
        return getThis();
    }

    public RouterLike notFound(T target) {
        router.notFound(target);
        return getThis();
    }

    //--------------------------------------------------------------------------

    public void removeTarget(T target) {
        router.removeTarget(target);
    }

    public void removePath(String path) {
        router.removePath(path);
    }

    //--------------------------------------------------------------------------

    public Routed<Object> route(M method, String path) {
        return router.route(method, path);
    }

    //--------------------------------------------------------------------------
    // Reverse routing.

    public String path(M method, T target, Object... params) {
        return router.path(method, target, params);
    }

    public String path(T target, Object... params) {
        return router.path(target, params);
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

    //--------------------------------------------------------------------------

    public RouterLike pattern(M method, String path, Class<? extends T> target) {
        router.pattern(method, path, target);
        return getThis();
    }

    public RouterLike patternFirst(M method, String path, Class<? extends T> target) {
        router.patternFirst(method, path, target);
        return getThis();
    }

    public RouterLike patternLast(M method, String path, Class<? extends T> target) {
        router.patternLast(method, path, target);
        return getThis();
    }

    public RouterLike notFound(Class<? extends T> target) {
        router.notFound(target);
        return getThis();
    }

    public void removeTarget(Class<? extends T> target) {
        router.removeTarget(target);
    }

    public String path(M method, Class<? extends T> target, Object... params) {
        return router.path(method, target, params);
    }

    public String path(Class<? extends T> target, Object... params) {
        return router.path(target, params);
    }

    public RouterLike CONNECT(String path, Class<? extends T> target) {
        return pattern(CONNECT(), path, target);
    }

    public RouterLike DELETE(String path, Class<? extends T> target) {
        return pattern(DELETE(), path, target);
    }

    public RouterLike GET(String path, Class<? extends T> target) {
        return pattern(GET(), path, target);
    }

    public RouterLike HEAD(String path, Class<? extends T> target) {
        return pattern(HEAD(), path, target);
    }

    public RouterLike OPTIONS(String path, Class<? extends T> target) {
        return pattern(OPTIONS(), path, target);
    }

    public RouterLike PATCH(String path, Class<? extends T> target) {
        return pattern(PATCH(), path, target);
    }

    public RouterLike POST(String path, Class<? extends T> target) {
        return pattern(POST(), path, target);
    }

    public RouterLike PUT(String path, Class<? extends T> target) {
        return pattern(PUT(), path, target);
    }

    public RouterLike TRACE(String path, Class<? extends T> target) {
        return pattern(TRACE(), path, target);
    }

    public RouterLike ANY(String path, Class<? extends T> target) {
        return pattern(null, path, target);
    }

    public RouterLike CONNECT_FIRST(String path, Class<? extends T> target) {
        return patternFirst(CONNECT(), path, target);
    }

    public RouterLike DELETE_FIRST(String path, Class<? extends T> target) {
        return patternFirst(DELETE(), path, target);
    }

    public RouterLike GET_FIRST(String path, Class<? extends T> target) {
        return patternFirst(GET(), path, target);
    }

    public RouterLike HEAD_FIRST(String path, Class<? extends T> target) {
        return patternFirst(HEAD(), path, target);
    }

    public RouterLike OPTIONS_FIRST(String path, Class<? extends T> target) {
        return patternFirst(OPTIONS(), path, target);
    }

    public RouterLike PATCH_FIRST(String path, Class<? extends T> target) {
        return patternFirst(PATCH(), path, target);
    }

    public RouterLike POST_FIRST(String path, Class<? extends T> target) {
        return patternFirst(POST(), path, target);
    }

    public RouterLike PUT_FIRST(String path, Class<? extends T> target) {
        return patternFirst(PUT(), path, target);
    }

    public RouterLike TRACE_FIRST(String path, Class<? extends T> target) {
        return patternFirst(TRACE(), path, target);
    }

    public RouterLike ANY_FIRST(String path, Class<? extends T> target) {
        return patternFirst(null, path, target);
    }

    public RouterLike CONNECT_LAST(String path, Class<? extends T> target) {
        return patternLast(CONNECT(), path, target);
    }

    public RouterLike DELETE_LAST(String path, Class<? extends T> target) {
        return patternLast(DELETE(), path, target);
    }

    public RouterLike GET_LAST(String path, Class<? extends T> target) {
        return patternLast(GET(), path, target);
    }

    public RouterLike HEAD_LAST(String path, Class<? extends T> target) {
        return patternLast(HEAD(), path, target);
    }

    public RouterLike OPTIONS_LAST(String path, Class<? extends T> target) {
        return patternLast(OPTIONS(), path, target);
    }

    public RouterLike PATCH_LAST(String path, Class<? extends T> target) {
        return patternLast(PATCH(), path, target);
    }

    public RouterLike POST_LAST(String path, Class<? extends T> target) {
        return patternLast(POST(), path, target);
    }

    public RouterLike PUT_LAST(String path, Class<? extends T> target) {
        return patternLast(PUT(), path, target);
    }

    public RouterLike TRACE_LAST(String path, Class<? extends T> target) {
        return patternLast(TRACE(), path, target);
    }

    public RouterLike ANY_LAST(String path, Class<? extends T> target) {
        return patternLast(null, path, target);
    }
}
