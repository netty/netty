package io.netty.handler.codec.http.routing;

import java.util.HashMap;
import java.util.Map;

import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.routing.StringRouter.*;

import static org.junit.Assert.*;
import org.junit.Test;

public class ReverseRoutingTest {
    @Test
    public void testHandleMethod() {
        assertEquals("/articles", router.path(GET, "index"));

        assertEquals("/articles/123", router.path(GET,  "show", "id", "123"));

        assertEquals("/anyMethod", router.path(GET,  "anyMethod"));
        assertEquals("/anyMethod", router.path(POST, "anyMethod"));
        assertEquals("/anyMethod", router.path(PUT,  "anyMethod"));
    }

    @Test
    public void testHandleEmptyParams() {
        assertEquals("/articles", router.path("index"));
    }

    @Test
    public void testHandleMapParams() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("id", 123);
        assertEquals("/articles/123", router.path("show", map));
    }

    @Test
    public void testHandleVarargs() {
        assertEquals("/download/foo/bar.png", router.path("download", "*", "foo/bar.png"));
    }

    @Test
    public void testReturnPathWithMinimumNumberOfParams() {
        Map<String, Object> map1 = new HashMap<String, Object>();
        map1.put("id",     123);
        map1.put("format", "json");
        assertEquals("/articles/123/json", router.path("show", map1));

        Map<String, Object> map2 = new HashMap<String, Object>();
        map2.put("id",     123);
        map2.put("format", "json");
        map2.put("x",      1);
        map2.put("y",      2);
        String path = router.path("show", map2);
        boolean matched1 = path.equals("/articles/123/json?x=1&y=2");
        boolean matched2 = path.equals("/articles/123/json?y=2&x=1");
        assertEquals(true, matched1 || matched2);
    }

    @Test
    public void testHandleClass() {
        MethodlessRouter<Object> router = new MethodlessRouter<Object>();
        Index index  = new Index();
        router.pattern("/articles",     index);
        router.pattern("/articles/:id", Show.class);

        assertEquals("/articles", router.path(index));
        assertEquals("/articles", router.path(Index.class));

        assertEquals(null,            router.path(new Show()));
        assertEquals("/articles/123", router.path(Show.class, "id", "123"));

        assertNotEquals(null, router.path(Action.class));
    }
}
