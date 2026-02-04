/*
 * Copyright 2025 The Netty Project
 *
 * Example showing CNAME tracking functionality in DNS resolution.
 */
package io.netty.resolver.dns;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.Future;

import java.util.List;

/**
 * Example demonstrating CNAME tracking functionality.
 */
public class CnameTrackingExample {

    public static void main(String[] args) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        
        try {
            DnsNameResolver resolver = new DnsNameResolverBuilder(group.next())
                    .channelType(NioDatagramChannel.class)
                    .build();
            
            try {
                String hostname = "fairplay-pdc.amp.apple.com";
                
                // Traditional resolution (no CNAME chain information)
                System.out.println("=== Traditional Resolution ===");
                Future<java.net.InetAddress> traditionalFuture = resolver.resolve(hostname);
                java.net.InetAddress address = traditionalFuture.sync().getNow();
                System.out.println("Resolved address: " + address);
                
                // New CNAME-aware resolution (single result)
                System.out.println("\n=== CNAME-Aware Resolution (Single) ===");
                Future<DnsResolveResult> cnamesFuture = resolver.resolveWithCnames(hostname);
                DnsResolveResult result = cnamesFuture.sync().getNow();
                
                System.out.println("Resolved address: " + result.address());
                System.out.println("CNAME chain: " + result.cnameChain());
                System.out.println("Has CNAME indirection: " + result.hasCnameIndirection());
                
                // New CNAME-aware resolution (all results)
                System.out.println("\n=== CNAME-Aware Resolution (All) ===");
                Future<List<DnsResolveResult>> allCnamesFuture = resolver.resolveAllWithCnames(hostname);
                List<DnsResolveResult> results = allCnamesFuture.sync().getNow();
                
                System.out.println("Found " + results.size() + " address(es):");
                for (int i = 0; i < results.size(); i++) {
                    DnsResolveResult r = results.get(i);
                    System.out.println("  [" + i + "] Address: " + r.address().getAddress() +
                                     ", CNAME chain: " + r.cnameChain());
                }
                
                // Demonstrate cache behavior
                System.out.println("\n=== Cache Demonstration ===");
                System.out.println("Second resolution (should hit cache):");
                Future<DnsResolveResult> cachedFuture = resolver.resolveWithCnames(hostname);
                DnsResolveResult cachedResult = cachedFuture.sync().getNow();
                System.out.println("Cached result: " + cachedResult.address() +
                                 ", CNAME chain: " + cachedResult.cnameChain());
                
            } finally {
                resolver.close();
            }
        } finally {
            group.shutdownGracefully();
        }
    }
}