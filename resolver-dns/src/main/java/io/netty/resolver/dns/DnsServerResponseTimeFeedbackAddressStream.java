package io.netty.resolver.dns;

import java.net.InetSocketAddress;

/**
 * An infinite stream of DNS server addresses, that requests feedback to be returned to it.
 */
public interface DnsServerResponseTimeFeedbackAddressStream extends DnsServerAddressStream {

    /**
     * A way to provide timing feedback to the {@link DnsServerAddressStream} so that {@link #next()} can be tuned
     * to return the best performing DNS server address
     *
     * @param address The address returned by {@link #next()} that feedback needs to be applied to
     * @param queryResponseTimeNanos The response time of a query against the given DNS server
     */
    void feedbackResponseTime(InetSocketAddress address, long queryResponseTimeNanos);
}
