/*
 * Copyright 2015 The Netty Project
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

import static io.netty.handler.codec.http.HttpMethod.*;

import static org.junit.Assert.*;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

public class RoutingTest {
    @Test
    public void testIgnoreSlashesAtBothEnds() {
        Assert.assertEquals("index", StringRouter.router.route(GET, "articles").target());
        Assert.assertEquals("index", StringRouter.router.route(GET, "/articles").target());
        Assert.assertEquals("index", StringRouter.router.route(GET, "//articles").target());
        Assert.assertEquals("index", StringRouter.router.route(GET, "articles/").target());
        Assert.assertEquals("index", StringRouter.router.route(GET, "articles//").target());
        Assert.assertEquals("index", StringRouter.router.route(GET, "/articles/").target());
        Assert.assertEquals("index", StringRouter.router.route(GET, "//articles//").target());
    }

    @Test
    public void testHandleEmptyParams() {
        RouteResult<String> routed = StringRouter.router.route(GET, "/articles");
        assertEquals("index", routed.target());
        assertEquals(0,       routed.pathParams().size());
    }

    @Test
    public void testHandleParams() {
        RouteResult<String> routed = StringRouter.router.route(GET, "/articles/123");
        assertEquals("show", routed.target());
        assertEquals(1,      routed.pathParams().size());
        assertEquals("123",  routed.pathParams().get("id"));
    }

    @Test
    public void testHandleNone() {
        MethodlessRouter<String> router = new MethodlessRouter<String>().addRoute("/articles", "index");
        RouteResult<String> routed = router.route("/noexist");
        assertEquals(true, routed == null);
    }

    @Test
    public void testHandleSplatWildcard() {
        RouteResult<String> routed = StringRouter.router.route(GET, "/download/foo/bar.png");
        assertEquals("download",    routed.target());
        assertEquals(1,             routed.pathParams().size());
        assertEquals("foo/bar.png", routed.pathParams().get("*"));
    }

    @Test
    public void testHandleOrder() {
        RouteResult<String> routed1 = StringRouter.router.route(GET, "/articles/new");
        assertEquals("new", routed1.target());
        assertEquals(0,     routed1.pathParams().size());

        RouteResult<String> routed2 = StringRouter.router.route(GET, "/articles/123");
        assertEquals("show", routed2.target());
        assertEquals(1,      routed2.pathParams().size());
        assertEquals("123",  routed2.pathParams().get("id"));

        RouteResult<String> routed3 = StringRouter.router.route(GET, "/notfound");
        assertEquals("404", routed3.target());
        assertEquals(0,     routed3.pathParams().size());
    }

    @Test
    public void testHandleAnyMethod() {
        RouteResult<String> routed1 = StringRouter.router.route(GET, "/anyMethod");
        assertEquals("anyMethod", routed1.target());
        assertEquals(0,           routed1.pathParams().size());

        RouteResult<String> routed2 = StringRouter.router.route(POST, "/anyMethod");
        assertEquals("anyMethod", routed2.target());
        assertEquals(0,           routed2.pathParams().size());
    }

    @Test
    public void testHandleRemoveByTarget() {
        MethodlessRouter<String> router = new MethodlessRouter<String>().addRoute("/articles", "index");
        router.removeTarget("index");
        RouteResult<String> routed = router.route("/articles");
        assertEquals(true, routed == null);
    }

    @Test
    public void testHandleRemoveByPath() {
        MethodlessRouter<String> router = new MethodlessRouter<String>().addRoute("/articles", "index");
        router.removePath("/articles");
        RouteResult<String> routed = router.route("/articles");
        assertEquals(true, routed == null);
    }

    @Test
    public void testAllowedMethods() {
        assertEquals(9, StringRouter.router.allAllowedMethods().size());

        Set<HttpMethod> methods = StringRouter.router.allowedMethods("/articles");
        assertEquals(2, methods.size());
        assertTrue(methods.contains(HttpMethod.GET));
        assertTrue(methods.contains(HttpMethod.POST));
    }

    @Test
    public void testHandleSubclasses() {
        MethodlessRouter<Class<? extends Action>> router = new MethodlessRouter<Class<? extends Action>>();
        router.addRoute("/articles",     Index.class);
        router.addRoute("/articles/:id", Show.class);

        RouteResult<Class<? extends Action>> routed1 = router.route("/articles");
        RouteResult<Class<? extends Action>> routed2 = router.route("/articles/123");
        assertNotNull(routed1);
        assertNotNull(routed2);
        assertEquals(Index.class, routed1.target());
        assertEquals(Show.class,  routed2.target());
    }
}
