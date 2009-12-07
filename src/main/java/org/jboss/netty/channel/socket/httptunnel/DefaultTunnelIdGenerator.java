package org.jboss.netty.channel.socket.httptunnel;

import java.security.SecureRandom;

public class DefaultTunnelIdGenerator implements TunnelIdGenerator {

    private SecureRandom generator;
    
    public DefaultTunnelIdGenerator() {
        this(new SecureRandom());
    }
    
    public DefaultTunnelIdGenerator(SecureRandom generator) {
        this.generator = generator;
    }
    
    public synchronized String generateId() {
        // synchronized to ensure that this code is thread safe. The Sun
        // standard implementations seem to be synchronized or lock free
        // but are not documented as guaranteeing this
        return Integer.toString(generator.nextInt());
    }

}
