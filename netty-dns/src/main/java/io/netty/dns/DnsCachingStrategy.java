package io.netty.dns;

import java.util.List;

public interface DnsCachingStrategy {

    public <T> T getRecord(String name, int type);

    public <T> List<T> getRecords(String name, int type);

    public <T> void submitRecord(String name, int type, long ttl, T content);

}
