package io.netty.handler.codec.memcache.ascii.request;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheRequest;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheRequest;

import java.util.Collection;

public class AsciiMemcacheRetrieveRequest extends AbstractAsciiMemcacheRequest {

    private final String[] keys;
    private final RetrieveCommand command;

    public AsciiMemcacheRetrieveRequest(RetrieveCommand command, String key) {
        this(command, new String[] { key });
    }

    public AsciiMemcacheRetrieveRequest(RetrieveCommand command, Collection<String> keys) {
        this(command, keys.toArray(new String[] {}));
    }

    public AsciiMemcacheRetrieveRequest(RetrieveCommand command, String... keys) {
        this.command = command;
        this.keys = keys;
    }

    public String[] getKeys() {
        return keys;
    }

    public RetrieveCommand getCommand() {
        return command;
    }

    @Override
    public AsciiMemcacheRetrieveRequest setNoreply(boolean noreply) {
        throw new IllegalStateException("noreply is not supported on the retrieve request.");
    }

    public static enum RetrieveCommand {
        GET("get"),
        GETS("gets");

        private final String value;

        RetrieveCommand(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
