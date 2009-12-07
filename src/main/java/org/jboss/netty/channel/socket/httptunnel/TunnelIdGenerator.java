package org.jboss.netty.channel.socket.httptunnel;

public interface TunnelIdGenerator {

    /**
     * Generates the next tunnel ID to be used, which must be unique
     * (i.e. ensure with high probability that it will not clash with
     * an existing tunnel ID). This method must be thread safe, and
     * preferably lock free.
     */
    public String generateId();
    
}
