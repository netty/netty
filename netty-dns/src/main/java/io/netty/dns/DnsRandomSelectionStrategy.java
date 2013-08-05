package io.netty.dns;

import java.util.List;

public class DnsRandomStrategy implements DnsSelectionStrategy {

    @Override
    public <T> T selectRecord(List<T> records) {
        return records.get((int) (Math.random() * records.size()));
    }

}
