package io.netty.handler.codec.http.cache;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Date;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http.cache.CacheControlDecoder.MAXIMUM_AGE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpResponseFromCacheGeneratorTest {
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    private HttpResponseFromCacheGenerator responseGenerator;

    @Before
    public void setUp() throws Exception {
        responseGenerator = new HttpResponseFromCacheGenerator();
    }

    @Test
    public void contentLengthHeaderIsPopulated() {
        final byte[] content = {1, 2, 3, 4, 5};
        final HttpCacheEntry cacheEntry = cacheEntry(HttpResponseStatus.NO_CONTENT, 0L, Unpooled.wrappedBuffer(content));

        final FullHttpResponse response = responseGenerator.generate(request(), cacheEntry);

        assertThat(response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH), is(content.length));
    }

    @Test
    public void contentLengthHeaderIsNotPopulatedWhenTransferEncodingHeaderIsPresent() {
        final byte[] content = {1, 2, 3, 4, 5};
        final HttpCacheEntry cacheEntry = cacheEntry(HttpResponseStatus.NO_CONTENT, 0L,
                Unpooled.wrappedBuffer(content), HttpHeaderNames.TRANSFER_ENCODING, "gzip");

        final FullHttpResponse response = responseGenerator.generate(request(), cacheEntry);

        assertThat(response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH), nullValue());
    }

    @Test
    public void responseStatusMatchesCacheEntryStatus() {
        final HttpCacheEntry cacheEntry = cacheEntry(HttpResponseStatus.NO_CONTENT, 0L);

        final FullHttpResponse response = responseGenerator.generate(request(), cacheEntry);

        assertThat(response.status(), is(HttpResponseStatus.NO_CONTENT));
    }

    @Test
    public void ageHeaderIsPopulatedWithCurrentAgeOfCacheEntryIfNonZero() {
        final HttpCacheEntry cacheEntry = cacheEntry(HttpResponseStatus.OK, 1234L);

        final FullHttpResponse response = responseGenerator.generate(request(), cacheEntry);

        assertThat(response.headers().get(HttpHeaderNames.AGE), is("1234"));
    }

    @Test
    public void ageHeaderIsNotPopulatedWithCurrentAgeOfCacheEntryIfZero() {
        final HttpCacheEntry cacheEntry = cacheEntry(HttpResponseStatus.OK, 0L);

        final FullHttpResponse response = responseGenerator.generate(request(), cacheEntry);

        assertThat(response.headers().get(HttpHeaderNames.AGE), nullValue());
    }

    @Test
    public void ageHeaderIsPopulatedWithMaxAgeIfAgeTooBig() {
        final HttpCacheEntry cacheEntry = cacheEntry(HttpResponseStatus.OK, MAXIMUM_AGE + 1L);

        final FullHttpResponse response = responseGenerator.generate(request(), cacheEntry);

        assertThat(response.headers().get(HttpHeaderNames.AGE), is(Long.toString(MAXIMUM_AGE)));
    }

    private DefaultFullHttpRequest request(CharSequence... headerNameValuePairs) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/test", EMPTY_BUFFER,
                new ReadOnlyHttpHeaders(false, headerNameValuePairs), new ReadOnlyHttpHeaders(false));
    }

    private HttpCacheEntry cacheEntry(HttpResponseStatus status, long age) {
        return cacheEntry(status, age, EMPTY_BUFFER);
    }

    private HttpCacheEntry cacheEntry(HttpResponseStatus status, long age, ByteBuf content, CharSequence... headerNameValuePairs) {
        final HttpCacheEntry cacheEntry = mock(HttpCacheEntry.class);
        when(cacheEntry.getResponseHeaders()).thenReturn(new ReadOnlyHttpHeaders(false, headerNameValuePairs));
        when(cacheEntry.getCurrentAgeInSeconds(any(Date.class))).thenReturn(age);
        when(cacheEntry.getContent()).thenReturn(new DefaultByteBufHolder(content));
        when(cacheEntry.getStatus()).thenReturn(status);

        return cacheEntry;
    }


}