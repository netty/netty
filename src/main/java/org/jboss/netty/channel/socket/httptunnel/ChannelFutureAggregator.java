/**
 *
 */
package org.jboss.netty.channel.socket.httptunnel;

import java.util.HashSet;
import java.util.Set;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

class ChannelFutureAggregator implements ChannelFutureListener {

    private final ChannelFuture aggregateFuture;
    private final Set<ChannelFuture> pendingFutures;

    public ChannelFutureAggregator(ChannelFuture aggregateFuture) {
        this.aggregateFuture = aggregateFuture;
        pendingFutures = new HashSet<ChannelFuture>();
    }

    public void addFuture(ChannelFuture future) {
        pendingFutures.add(future);
        future.addListener(this);
    }

    public synchronized void operationComplete(ChannelFuture future) throws Exception {
        if (future.isCancelled()) {
            // TODO: what should the correct behaviour be when a fragment is cancelled?
            // cancel all outstanding fragments and cancel the aggregate?
            return;
        }

        pendingFutures.remove(future);
        if (!future.isSuccess()) {
            aggregateFuture.setFailure(future.getCause());
            for(ChannelFuture pendingFuture : pendingFutures) {
                pendingFuture.cancel();
            }
            return;
        }

        if (pendingFutures.isEmpty()) {
            aggregateFuture.setSuccess();
        }
    }
}