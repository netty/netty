package io.netty.handler.codec.memcache.ascii;

import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheResponseStatus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class AsciiMemcacheResponseStatusTest {

    @Test
    public void shouldReuseImmutableStatusTypes() {
        assertEquals(AsciiMemcacheResponseStatus.exists(), AsciiMemcacheResponseStatus.exists());
    }

    @Test
    public void shouldCreateNewMutableStatusType() {
        assertNotEquals(AsciiMemcacheResponseStatus.clientError(), AsciiMemcacheResponseStatus.clientError());
    }

}
