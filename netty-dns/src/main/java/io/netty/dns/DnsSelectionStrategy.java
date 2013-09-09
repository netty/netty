package io.netty.dns;

import java.util.List;

public interface DnsSelectionStrategy {

    public <T> T selectRecord(List<T> records);

}
