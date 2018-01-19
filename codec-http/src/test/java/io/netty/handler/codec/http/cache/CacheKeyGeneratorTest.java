package io.netty.handler.codec.http.cache;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class CacheKeyGeneratorTest {
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    private CacheKeyGenerator keyGenerator;

    @Before
    public void setUp() {
        keyGenerator = new CacheKeyGenerator();
    }

    @Test
    public void shouldGenerateSameKeyForSameRequest() {
        assertThat(keyGenerator.generateKey(request("example.com", "/test")),
                is(keyGenerator.generateKey(request("example.com", "/test"))));
    }

    @Ignore
    @Test
    public void shouldGenerateSameKeyForEquivalentRequest() {
        assertThat(keyGenerator.generateKey(request("example.com", "/test")),
                is(keyGenerator.generateKey(request("example.com:80", "/test"))));
    }

    @Test
    public void shouldGenerateDifferentKeyForDifferentHost() {
        assertThat(keyGenerator.generateKey(request("example.com", "/test")),
            is(not(keyGenerator.generateKey(request("example2.com", "/test")))));
    }

    @Test
    public void shouldGenerateDifferentKeyForDifferentPath() {
        assertThat(keyGenerator.generateKey(request("example.com", "/test")),
            is(not(keyGenerator.generateKey(request("example.com", "/test2")))));
    }

    @Ignore
    @Test
    public void shouldGenerateSameKeyForSameQueryParamInDifferentOrder() {
        assertThat(keyGenerator.generateKey(request("example.com", "/test?param1=1&param2=2")),
                is(keyGenerator.generateKey(request("example.com", "test?param2=2&param1=1"))));
    }

    private HttpRequest request(String host, String uri) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri, EMPTY_BUFFER, new ReadOnlyHttpHeaders(false, HOST, host), new ReadOnlyHttpHeaders(false));
    }


}