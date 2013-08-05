package io.netty.dns;

import java.util.List;

public class DnsRoundRobinStrategy implements DnsSelectionStrategy {

    private int index;

    @Override
    public <T> T selectRecord(List<T> records) {
        return records.get(index++ % records.size());
    }

}
