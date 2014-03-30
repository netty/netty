package io.netty.handler.codec.memcache.ascii.response;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheResponse;

import java.util.Map;

public class AsciiMemcacheStatsResponse extends AbstractAsciiMemcacheMessage implements AsciiMemcacheResponse {

    private final Map<String, String> stats;

    public AsciiMemcacheStatsResponse(final Map<String, String> stats) {
        this.stats = stats;
    }

    public Map<String, String> getStats() {
        return stats;
    }
}
