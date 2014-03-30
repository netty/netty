package io.netty.handler.codec.memcache.ascii.request;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheRequest;

public class AsciiMemcacheArithmeticRequest extends AbstractAsciiMemcacheMessage implements AsciiMemcacheRequest {

    private final String key;
    private final long amount;
    private boolean noreply;
    private final ArithmeticCommand command;

    public AsciiMemcacheArithmeticRequest(ArithmeticCommand command, String key, long amount) {
        this.command = command;
        this.key = key;
        this.amount = amount;
    }

    public String getKey() {
        return key;
    }

    public long getAmount() {
        return amount;
    }

    public boolean getNoreply() {
        return noreply;
    }

    public ArithmeticCommand getCommand() {
        return command;
    }

    public void setNoreply(boolean noreply) {
        this.noreply = noreply;
    }

    public static enum ArithmeticCommand {

        INCR("incr"),
        DECR("decr");

        private final String value;

        ArithmeticCommand(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
