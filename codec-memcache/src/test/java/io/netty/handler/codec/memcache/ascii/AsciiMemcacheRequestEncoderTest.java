package io.netty.handler.codec.memcache.ascii;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheArithmeticRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheDeleteRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheFlushRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheQuitRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheRetrieveRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheStatsRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheStoreRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheTouchRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheVersionRequest;
import io.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * Verifies the functionality of the {@link AsciiMemcacheRequestEncoder}.
 */
public class AsciiMemcacheRequestEncoderTest {

    private EmbeddedChannel channel;

    @Before
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new AsciiMemcacheRequestEncoder());
    }

    @Test
    public void shouldEncodeStoreRequest() {
        AsciiMemcacheStoreRequest request = new AsciiMemcacheStoreRequest(
            AsciiMemcacheStoreRequest.StorageCommand.SET,
            "foobar",
            50
        );
        writeAndAssertOutbound(request, "set foobar 0 0 50");
    }

    @Test
    public void shouldEncodeStoreWithNoReplyRequest() {
        AsciiMemcacheStoreRequest request = new AsciiMemcacheStoreRequest(
            AsciiMemcacheStoreRequest.StorageCommand.SET,
            "foobar",
            50
        );
        request.setNoreply(true);
        writeAndAssertOutbound(request, "set foobar 0 0 50 noreply");
    }

    @Test
    public void shouldEncodeStoreWithCasRequest() {
        AsciiMemcacheStoreRequest request = new AsciiMemcacheStoreRequest(
            AsciiMemcacheStoreRequest.StorageCommand.CAS,
            "foobar",
            50
        );
        request.setCas(12345);
        writeAndAssertOutbound(request, "cas foobar 0 0 50 12345");
    }

    @Test
    public void shouldEncodeStoreWithAllOptionsSet() {
        AsciiMemcacheStoreRequest request = new AsciiMemcacheStoreRequest(
            AsciiMemcacheStoreRequest.StorageCommand.CAS,
            "foobar",
            50
        );

        request.setCas(12345)
            .setExpiration(20)
            .setFlags(34)
            .setNoreply(true);

        writeAndAssertOutbound(request, "cas foobar 34 20 50 12345 noreply");
    }

    @Test
    public void shouldEncodeGetRequest() {
        AsciiMemcacheRetrieveRequest request = new AsciiMemcacheRetrieveRequest(
            AsciiMemcacheRetrieveRequest.RetrieveCommand.GET,
            "foo"
        );
        writeAndAssertOutbound(request, "get foo");
    }

    @Test
    public void shouldEncodeGetsRequest() {
        AsciiMemcacheRetrieveRequest request = new AsciiMemcacheRetrieveRequest(
            AsciiMemcacheRetrieveRequest.RetrieveCommand.GETS,
            "foo"
        );
        writeAndAssertOutbound(request, "gets foo");
    }

    @Test
    public void shouldEncodeRetrieveWithMultipleKeys() {
        AsciiMemcacheRetrieveRequest request = new AsciiMemcacheRetrieveRequest(
            AsciiMemcacheRetrieveRequest.RetrieveCommand.GET,
            "foo", "bar", "baz"
        );
        writeAndAssertOutbound(request, "get foo bar baz");
    }

    @Test
    public void shouldEncodeDeleteRequest() {
        AsciiMemcacheDeleteRequest request = new AsciiMemcacheDeleteRequest("foo", true);
        writeAndAssertOutbound(request, "delete foo noreply");
    }

    @Test
    public void shouldEncodeArithmeticRequest() {
        AsciiMemcacheArithmeticRequest request = new AsciiMemcacheArithmeticRequest(
            AsciiMemcacheArithmeticRequest.ArithmeticCommand.INCR,
            "foo",
            5
        );
        writeAndAssertOutbound(request, "incr foo 5");
    }

    @Test
    public void shouldEncodeTouchRequest() {
        AsciiMemcacheTouchRequest request = new AsciiMemcacheTouchRequest(
            "foo",
            10,
            true
        );
        writeAndAssertOutbound(request, "touch foo 10 noreply");
    }

    @Test
    public void shouldEncodeVersionRequest() {
        AsciiMemcacheVersionRequest request = new AsciiMemcacheVersionRequest();
        writeAndAssertOutbound(request, "version");
    }

    @Test
    public void shouldEncodeFlushRequest() {
        AsciiMemcacheFlushRequest request = new AsciiMemcacheFlushRequest();
        writeAndAssertOutbound(request, "flush_all");
    }

    @Test
    public void shouldEncodeQuitRequest() {
        AsciiMemcacheQuitRequest request = new AsciiMemcacheQuitRequest();
        writeAndAssertOutbound(request, "quit");
    }

    @Test
    public void shouldEncodeStatsRequest() {
        AsciiMemcacheStatsRequest request = new AsciiMemcacheStatsRequest("foo", "bar");
        writeAndAssertOutbound(request, "stats foo bar");
    }

    /**
     * Helper method to assert a string on the outbound channel side.
     *
     * Note that for convenience, the expected string does not need to contain
     * the newline characters, those will be added automatically.
     *
     * @param req the request to write.
     * @param expected the expected string written out.
     */
    private void writeAndAssertOutbound(AsciiMemcacheRequest req, String expected) {
        boolean result = channel.writeOutbound(req);
        assertThat(result, is(true));

        ByteBuf written = (ByteBuf) channel.readOutbound();
        assertThat(written.toString(CharsetUtil.UTF_8), is(expected + "\r\n"));
        written.release();
    }

}
