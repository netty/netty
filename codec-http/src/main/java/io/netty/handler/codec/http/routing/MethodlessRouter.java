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

import java.util.Collections;

public class MethodlessRouter<T> {
    protected final NonorderedRouter<T> first = new NonorderedRouter<T>();
    protected final NonorderedRouter<T> other = new NonorderedRouter<T>();
    protected final NonorderedRouter<T> last  = new NonorderedRouter<T>();

    protected T notFound;

    //--------------------------------------------------------------------------

    public NonorderedRouter<T> first() { return first; }
    public NonorderedRouter<T> other() { return other; }
    public NonorderedRouter<T> last () { return last ; }

    public T notFound() { return notFound; }

    //--------------------------------------------------------------------------

    public MethodlessRouter<T> pattern(String path, T target) {
        other.pattern(path, target);
        return this;
    }

    public MethodlessRouter<T> patternFirst(String path, T target) {
        first.pattern(path, target);
        return this;
    }

    public MethodlessRouter<T> patternLast(String path, T target) {
        last.pattern(path, target);
        return this;
    }

    public MethodlessRouter<T> notFound(T target) {
        this.notFound = target;
        return this;
    }

    //--------------------------------------------------------------------------

    public void removeTarget(T target) {
        first.removeTarget(target);
        other.removeTarget(target);
        last .removeTarget(target);
    }

    public void removePath(String path) {
        first.removePath(path);
        other.removePath(path);
        last .removePath(path);
    }

    //--------------------------------------------------------------------------

    public Routed<T> route(String path) {
        Routed<T> ret = first.route(path);
        if (ret != null) { return ret; }

        ret = other.route(path);
        if (ret != null) { return ret; }

        ret = last.route(path);
        if (ret != null) { return ret; }

        if (notFound != null) { return new Routed<T>(notFound, true, Collections.<String, String>emptyMap()); }

        return null;
    }

    public String path(T target, Object... params) {
        String ret = first.path(target, params);
        if (ret != null) { return ret; }

        ret = other.path(target, params);
        if (ret != null) { return ret; }

        return last.path(target, params);
    }
}
