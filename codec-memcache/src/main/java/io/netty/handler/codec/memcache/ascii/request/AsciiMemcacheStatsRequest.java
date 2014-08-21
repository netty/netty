package io.netty.handler.codec.memcache.ascii.request;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheRequest;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheRequest;

import java.util.Collection;


public class AsciiMemcacheStatsRequest extends AbstractAsciiMemcacheRequest {

    private final String[] stats;

    public AsciiMemcacheStatsRequest(String stat) {
        this(new String[] { stat });
    }

    public AsciiMemcacheStatsRequest(Collection<String> stats) {
        this(stats.toArray(new String[] {}));
    }

    public AsciiMemcacheStatsRequest(String... stats) {
        this.stats = stats;
    }

    public String[] getStats() {
        return stats;
    }

    @Override
    public AsciiMemcacheStatsRequest setNoreply(boolean noreply) {
        throw new IllegalStateException("noreply is not supported on the stats request.");
    }
}
