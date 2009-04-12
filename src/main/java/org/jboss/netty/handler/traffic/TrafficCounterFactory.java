/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.traffic;

import java.util.concurrent.ExecutorService;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.internal.ExecutorUtil;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Frederic Bregier (fredbregier@free.fr)
 * @version $Rev$, $Date$
 *
 * The {@link TrafficCounterFactory} is a Factory for
 * {@link TrafficCounter}. It stores the necessary information to enable
 * dynamic creation of {@link TrafficCounter} inside the
 * {@link TrafficShapingHandler}.
 *
 *
 */
public class TrafficCounterFactory implements ExternalResourceReleasable {
    // FIXME: Use Executor instead of ExecutorService
    // TODO: Read/write limit needs to be configurable on a per-channel basis.

    /**
     * Default delay between two checks: 1s
     */
    public static long DEFAULT_CHECK_INTERVAL = 1000;

    /**
     * ExecutorService to associated to any TrafficCounter
     */
    private ExecutorService executorService = null;

    /**
     * Limit in B/s to apply to write for all channel TrafficCounter
     */
    private long channelWriteLimit = 0;

    /**
     * Limit in B/s to apply to read for all channel TrafficCounter
     */
    private long channelReadLimit = 0;

    /**
     * Delay between two performance snapshots for channel
     */
    private long channelCheckInterval = DEFAULT_CHECK_INTERVAL; // default 1 s

    /**
     * Will the TrafficCounter for Channel be active
     */
    private boolean channelActive = true;

    /**
     * Limit in B/s to apply to write for the global TrafficCounter
     */
    private long globalWriteLimit = 0;

    /**
     * Limit in B/s to apply to read for the global TrafficCounter
     */
    private long globalReadLimit = 0;

    /**
     * Delay between two performance snapshots for global
     */
    private long globalCheckInterval = DEFAULT_CHECK_INTERVAL; // default 1 s

    /**
     * Will the TrafficCounter for Global be active
     */
    private boolean globalActive = true;

    /**
     * Global Monitor
     */
    private TrafficCounter globalTrafficMonitor = null;

    /**
     * Called each time the accounting is computed for the TrafficCounters.
     * This method could be used for instance to implement real time accounting.
     *
     * @param counter
     *            the TrafficCounter that computes its performance
     */
    protected void doAccounting(TrafficCounter counter) {
        // NOOP by default
    }

    /**
     *
     * @param newexecutorService
     * @param newChannelActive
     * @param newChannelWriteLimit
     * @param newChannelReadLimit
     * @param newChannelCheckInterval
     * @param newGlobalActive
     * @param newGlobalWriteLimit
     * @param newGlobalReadLimit
     * @param newGlobalCheckInterval
     */
    private void init(ExecutorService newexecutorService,
            boolean newChannelActive, long newChannelWriteLimit,
            long newChannelReadLimit, long newChannelCheckInterval,
            boolean newGlobalActive, long newGlobalWriteLimit,
            long newGlobalReadLimit, long newGlobalCheckInterval) {
        executorService = newexecutorService;
        channelActive = newChannelActive;
        channelWriteLimit = newChannelWriteLimit;
        channelReadLimit = newChannelReadLimit;
        channelCheckInterval = newChannelCheckInterval;
        globalActive = newGlobalActive;
        globalWriteLimit = newGlobalWriteLimit;
        globalReadLimit = newGlobalReadLimit;
        globalCheckInterval = newGlobalCheckInterval;
    }

    /**
     * Full constructor
     *
     * @param executorService
     *            created for instance like Executors.newCachedThreadPool
     * @param channelActive
     *            True if each channel will have a TrafficCounter
     * @param channelWriteLimit
     *            0 or a limit in bytes/s
     * @param channelReadLimit
     *            0 or a limit in bytes/s
     * @param channelCheckInterval
     *            The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     * @param globalActive
     *            True if global context will have one unique TrafficCounter
     * @param globalWriteLimit
     *            0 or a limit in bytes/s
     * @param globalReadLimit
     *            0 or a limit in bytes/s
     * @param globalCheckInterval
     *            The delay between two computations of performances for global
     *            context or 0 if no stats are to be computed
     */
    public TrafficCounterFactory(ExecutorService executorService,
            boolean channelActive, long channelWriteLimit,
            long channelReadLimit, long channelCheckInterval, boolean globalActive,
            long globalWriteLimit, long globalReadLimit, long globalCheckInterval) {
        init(executorService, channelActive, channelWriteLimit,
                channelReadLimit, channelCheckInterval, globalActive, globalWriteLimit,
                globalReadLimit, globalCheckInterval);
    }

    /**
     * Constructor using default Delay
     *
     * @param executorService
     *            created for instance like Executors.newCachedThreadPool
     * @param channelActive
     *            True if each channel will have a TrafficCounter
     * @param channelWriteLimit
     *            0 or a limit in bytes/s
     * @param channelReadLimit
     *            0 or a limit in bytes/s
     * @param globalActive
     *            True if global context will have one unique TrafficCounter
     * @param globalWriteLimit
     *            0 or a limit in bytes/s
     * @param globalReadLimit
     *            0 or a limit in bytes/s
     */
    public TrafficCounterFactory(ExecutorService executorService,
            boolean channelActive, long channelWriteLimit,
            long channelReadLimit, boolean globalActive, long globalWriteLimit,
            long globalReadLimit) {
        init(executorService, channelActive, channelWriteLimit,
                channelReadLimit, DEFAULT_CHECK_INTERVAL, globalActive,
                globalWriteLimit, globalReadLimit, DEFAULT_CHECK_INTERVAL);
    }

    /**
     * Constructor using NO LIMIT and default delay for channels
     *
     * @param executorService
     *            created for instance like Executors.newCachedThreadPool
     * @param channelActive
     *            True if each channel will have a TrafficCounter
     * @param globalActive
     *            True if global context will have one unique TrafficCounter
     * @param globalWriteLimit
     *            0 or a limit in bytes/s
     * @param globalReadLimit
     *            0 or a limit in bytes/s
     * @param globalCheckInterval
     *            The delay between two computations of performances for global
     *            context or NO_STAT if no stats are to be computed
     */
    public TrafficCounterFactory(ExecutorService executorService,
            boolean channelActive, boolean globalActive, long globalWriteLimit,
            long globalReadLimit, long globalCheckInterval) {
        init(executorService, channelActive, 0, 0,
                DEFAULT_CHECK_INTERVAL, globalActive, globalWriteLimit, globalReadLimit,
                globalCheckInterval);
    }

    /**
     * Constructor using NO LIMIT for channels and default delay for all
     *
     * @param executorService
     *            created for instance like Executors.newCachedThreadPool
     * @param channelActive
     *            True if each channel will have a TrafficCounter
     * @param globalActive
     *            True if global context will have one unique TrafficCounter
     * @param globalWriteLimit
     *            0 or a limit in bytes/s
     * @param globalReadLimit
     *            0 or a limit in bytes/s
     */
    public TrafficCounterFactory(ExecutorService executorService,
            boolean channelActive, boolean globalActive, long globalWriteLimit,
            long globalReadLimit) {
        init(executorService, channelActive, 0, 0,
                DEFAULT_CHECK_INTERVAL, globalActive, globalWriteLimit, globalReadLimit,
                DEFAULT_CHECK_INTERVAL);
    }

    /**
     * Constructor using NO LIMIT and default delay for all
     *
     * @param executorService
     *            created for instance like Executors.newCachedThreadPool
     * @param channelActive
     *            True if each channel will have a TrafficCounter
     * @param globalActive
     *            True if global context will have one unique TrafficCounter
     */
    public TrafficCounterFactory(ExecutorService executorService,
            boolean channelActive, boolean globalActive) {
        init(executorService, channelActive, 0, 0,
                DEFAULT_CHECK_INTERVAL, globalActive, 0, 0, DEFAULT_CHECK_INTERVAL);
    }

    /**
     * Enable to change the active status of TrafficCounter on Channels (for
     * new one only)
     *
     * @param active
     */
    public void setChannelActive(boolean active) {
        channelActive = active;
    }

    /**
     * Enable to change the active status of TrafficCounter on Global (stop
     * or start if necessary)
     *
     * @param active
     */
    public void setGlobalActive(boolean active) {
        if (globalActive) {
            if (!active) {
                stopGlobalTrafficCounter();
            }
        }
        globalActive = active;
        getGlobalTrafficCounter();
    }

    /**
     * Change the underlying limitations. Only Global TrafficCounter (if
     * any) is dynamically changed, but Channels TrafficCounters are not
     * changed, only new created ones.
     *
     * @param newchannelWriteLimit
     * @param newchannelReadLimit
     * @param newchannelCheckInterval
     * @param newglobalWriteLimit
     * @param newglobalReadLimit
     * @param newGlobalCheckInterval
     */
    public void configure(long newchannelWriteLimit,
            long newchannelReadLimit, long newchannelCheckInterval,
            long newglobalWriteLimit, long newglobalReadLimit,
            long newGlobalCheckInterval) {
        channelWriteLimit = newchannelWriteLimit;
        channelReadLimit = newchannelReadLimit;
        channelCheckInterval = newchannelCheckInterval;
        globalWriteLimit = newglobalWriteLimit;
        globalReadLimit = newglobalReadLimit;
        globalCheckInterval = newGlobalCheckInterval;
        if (globalTrafficMonitor != null) {
            globalTrafficMonitor.configure(null,
                    newglobalWriteLimit, newglobalReadLimit, newGlobalCheckInterval);
        }
    }

    /**
     * @return the Global TrafficCounter or null if this support is disabled
     */
    public TrafficCounter getGlobalTrafficCounter() {
        if (globalActive) {
            if (globalTrafficMonitor == null) {
                globalTrafficMonitor = new TrafficCounter(this,
                        executorService, null, "GlobalPC",
                        globalWriteLimit, globalReadLimit,
                        globalCheckInterval);
                globalTrafficMonitor.start();
            }
        }
        return globalTrafficMonitor;
    }

    /**
     * @param ctx 
     * @param e 
     * @return a new TrafficCount for the given channel or null if none are required
     *
     * @throws UnsupportedOperationException if per-channel counter is disabled
     */
    public TrafficCounter newChannelTrafficCounter(ChannelHandlerContext ctx, ChannelStateEvent e) 
    throws UnsupportedOperationException {
        Channel channel = ctx.getChannel();
        if (channelActive && (channelReadLimit > 0 || channelWriteLimit > 0
                || channelCheckInterval > 0)) {
            return new TrafficCounter(this, executorService, channel,
                    "ChannelTC" + channel.getId(), channelWriteLimit,
                    channelReadLimit, channelCheckInterval);
        }
        throw new UnsupportedOperationException("per-channel counter disabled");
    }

    /**
     * Stop the global TrafficCounter if any (Even it is stopped, the
     * factory can however be reused)
     *
     */
    public void stopGlobalTrafficCounter() {
        if (globalTrafficMonitor != null) {
            globalTrafficMonitor.stop();
            globalTrafficMonitor = null;
        }
    }

    /**
     * @return the channelCheckInterval
     */
    public long getChannelCheckInterval() {
        return channelCheckInterval;
    }

    /**
     * @param channelCheckInterval
     *            the channelCheckInterval to set
     */
    public void setChannelCheckInterval(long channelCheckInterval) {
        this.channelCheckInterval = channelCheckInterval;
    }

    /**
     * @return the channelReadLimit
     */
    public long getChannelReadLimit() {
        return channelReadLimit;
    }

    /**
     * @param channelReadLimit
     *            the channelReadLimit to set
     */
    public void setChannelReadLimit(long channelReadLimit) {
        this.channelReadLimit = channelReadLimit;
    }

    /**
     * @return the channelWriteLimit
     */
    public long getChannelWriteLimit() {
        return channelWriteLimit;
    }

    /**
     * @param channelWriteLimit
     *            the channelWriteLimit to set
     */
    public void setChannelWriteLimit(long channelWriteLimit) {
        this.channelWriteLimit = channelWriteLimit;
    }

    /**
     * @return the globalCheckInterval
     */
    public long getGlobalCheckInterval() {
        return globalCheckInterval;
    }

    /**
     * @param globalCheckInterval
     *            the globalCheckInterval to set
     */
    public void setGlobalCheckInterval(long globalCheckInterval) {
        this.globalCheckInterval = globalCheckInterval;
        if (globalTrafficMonitor != null) {
            globalTrafficMonitor.configure(null,
                    globalWriteLimit, globalReadLimit,
                    globalCheckInterval);
        }
    }

    /**
     * @return the globalReadLimit
     */
    public long getGlobalReadLimit() {
        return globalReadLimit;
    }

    /**
     * @param globalReadLimit
     *            the globalReadLimit to set
     */
    public void setGlobalReadLimit(long globalReadLimit) {
        this.globalReadLimit = globalReadLimit;
        if (globalTrafficMonitor != null) {
            globalTrafficMonitor.configure(null,
                    globalWriteLimit, this.globalReadLimit,
                    globalCheckInterval);
        }
    }

    /**
     * @return the globalWriteLimit
     */
    public long getGlobalWriteLimit() {
        return globalWriteLimit;
    }

    /**
     * @param globalWriteLimit
     *            the globalWriteLimit to set
     */
    public void setGlobalWriteLimit(long globalWriteLimit) {
        this.globalWriteLimit = globalWriteLimit;
        if (globalTrafficMonitor != null) {
            globalTrafficMonitor.configure(null,
                    this.globalWriteLimit, globalReadLimit,
                    globalCheckInterval);
        }
    }

    /**
     * @return the channelActive
     */
    public boolean isChannelActive() {
        return channelActive;
    }

    /**
     * @return the globalActive
     */
    public boolean isGlobalActive() {
        return globalActive;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.util.ExternalResourceReleasable#releaseExternalResources()
     */
    public void releaseExternalResources() {
        ExecutorUtil.terminate(this.executorService);
    }
}
