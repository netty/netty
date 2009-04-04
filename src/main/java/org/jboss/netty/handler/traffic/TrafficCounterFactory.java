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
public abstract class TrafficCounterFactory {
    /**
     * Default delay between two checks: 1s
     */
    public static long DEFAULT_DELAY = 1000;

    /**
     * ExecutorService to associated to any TrafficCounter
     */
    private ExecutorService executorService = null;

    /**
     * Limit in B/s to apply to write for all channel TrafficCounter
     */
    private long channelLimitWrite = 0;

    /**
     * Limit in B/s to apply to read for all channel TrafficCounter
     */
    private long channelLimitRead = 0;

    /**
     * Delay between two performance snapshots for channel
     */
    private long channelDelay = DEFAULT_DELAY; // default 1 s

    /**
     * Will the TrafficCounter for Channel be active
     */
    private boolean channelActive = true;

    /**
     * Limit in B/s to apply to write for the global TrafficCounter
     */
    private long globalLimitWrite = 0;

    /**
     * Limit in B/s to apply to read for the global TrafficCounter
     */
    private long globalLimitRead = 0;

    /**
     * Delay between two performance snapshots for global
     */
    private long globalDelay = DEFAULT_DELAY; // default 1 s

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
    protected abstract void accounting(TrafficCounter counter);

    /**
     *
     * @param newexecutorService
     * @param newChannelActive
     * @param newChannelLimitWrite
     * @param newChannelLimitRead
     * @param newChannelDelay
     * @param newGlobalActive
     * @param newGlobalLimitWrite
     * @param newGlobalLimitRead
     * @param newGlobalDelay
     */
    private void init(ExecutorService newexecutorService,
            boolean newChannelActive, long newChannelLimitWrite,
            long newChannelLimitRead, long newChannelDelay,
            boolean newGlobalActive, long newGlobalLimitWrite,
            long newGlobalLimitRead, long newGlobalDelay) {
        this.executorService = newexecutorService;
        this.channelActive = newChannelActive;
        this.channelLimitWrite = newChannelLimitWrite;
        this.channelLimitRead = newChannelLimitRead;
        this.channelDelay = newChannelDelay;
        this.globalActive = newGlobalActive;
        this.globalLimitWrite = newGlobalLimitWrite;
        this.globalLimitRead = newGlobalLimitRead;
        this.globalDelay = newGlobalDelay;
    }

    /**
     * Full constructor
     *
     * @param executorService
     *            created for instance like Executors.newCachedThreadPool
     * @param channelActive
     *            True if each channel will have a TrafficCounter
     * @param channelLimitWrite
     *            NO_LIMIT or a limit in bytes/s
     * @param channelLimitRead
     *            NO_LIMIT or a limit in bytes/s
     * @param channelDelay
     *            The delay between two computations of performances for
     *            channels or NO_STAT if no stats are to be computed
     * @param globalActive
     *            True if global context will have one unique TrafficCounter
     * @param globalLimitWrite
     *            NO_LIMIT or a limit in bytes/s
     * @param globalLimitRead
     *            NO_LIMIT or a limit in bytes/s
     * @param globalDelay
     *            The delay between two computations of performances for global
     *            context or NO_STAT if no stats are to be computed
     */
    public TrafficCounterFactory(ExecutorService executorService,
            boolean channelActive, long channelLimitWrite,
            long channelLimitRead, long channelDelay, boolean globalActive,
            long globalLimitWrite, long globalLimitRead, long globalDelay) {
        init(executorService, channelActive, channelLimitWrite,
                channelLimitRead, channelDelay, globalActive, globalLimitWrite,
                globalLimitRead, globalDelay);
    }

    /**
     * Constructor using default Delay
     *
     * @param executorService
     *            created for instance like Executors.newCachedThreadPool
     * @param channelActive
     *            True if each channel will have a TrafficCounter
     * @param channelLimitWrite
     *            NO_LIMIT or a limit in bytes/s
     * @param channelLimitRead
     *            NO_LIMIT or a limit in bytes/s
     * @param globalActive
     *            True if global context will have one unique TrafficCounter
     * @param globalLimitWrite
     *            NO_LIMIT or a limit in bytes/s
     * @param globalLimitRead
     *            NO_LIMIT or a limit in bytes/s
     */
    public TrafficCounterFactory(ExecutorService executorService,
            boolean channelActive, long channelLimitWrite,
            long channelLimitRead, boolean globalActive, long globalLimitWrite,
            long globalLimitRead) {
        init(executorService, channelActive, channelLimitWrite,
                channelLimitRead, DEFAULT_DELAY, globalActive,
                globalLimitWrite, globalLimitRead, DEFAULT_DELAY);
    }

    /**
     * Constructor using NO_LIMIT and default delay for channels
     *
     * @param executorService
     *            created for instance like Executors.newCachedThreadPool
     * @param channelActive
     *            True if each channel will have a TrafficCounter
     * @param globalActive
     *            True if global context will have one unique TrafficCounter
     * @param globalLimitWrite
     *            NO_LIMIT or a limit in bytes/s
     * @param globalLimitRead
     *            NO_LIMIT or a limit in bytes/s
     * @param globalDelay
     *            The delay between two computations of performances for global
     *            context or NO_STAT if no stats are to be computed
     */
    public TrafficCounterFactory(ExecutorService executorService,
            boolean channelActive, boolean globalActive, long globalLimitWrite,
            long globalLimitRead, long globalDelay) {
        init(executorService, channelActive, 0, 0,
                DEFAULT_DELAY, globalActive, globalLimitWrite, globalLimitRead,
                globalDelay);
    }

    /**
     * Constructor using NO_LIMIT for channels and default delay for all
     *
     * @param executorService
     *            created for instance like Executors.newCachedThreadPool
     * @param channelActive
     *            True if each channel will have a TrafficCounter
     * @param globalActive
     *            True if global context will have one unique TrafficCounter
     * @param globalLimitWrite
     *            NO_LIMIT or a limit in bytes/s
     * @param globalLimitRead
     *            NO_LIMIT or a limit in bytes/s
     */
    public TrafficCounterFactory(ExecutorService executorService,
            boolean channelActive, boolean globalActive, long globalLimitWrite,
            long globalLimitRead) {
        init(executorService, channelActive, 0, 0,
                DEFAULT_DELAY, globalActive, globalLimitWrite, globalLimitRead,
                DEFAULT_DELAY);
    }

    /**
     * Constructor using NO_LIMIT and default delay for all
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
                DEFAULT_DELAY, globalActive, 0, 0, DEFAULT_DELAY);
    }

    /**
     * Enable to change the active status of TrafficCounter on Channels (for
     * new one only)
     *
     * @param active
     */
    public void setChannelActive(boolean active) {
        this.channelActive = active;
    }

    /**
     * Enable to change the active status of TrafficCounter on Global (stop
     * or start if necessary)
     *
     * @param active
     */
    public void setGlobalActive(boolean active) {
        if (this.globalActive) {
            if (!active) {
                stopGlobalTrafficCounter();
            }
        }
        this.globalActive = active;
        getGlobalTrafficCounter();
    }

    /**
     * Change the underlying limitations. Only Global TrafficCounter (if
     * any) is dynamically changed, but Channels TrafficCounters are not
     * changed, only new created ones.
     *
     * @param newchannelLimitWrite
     * @param newchannelLimitRead
     * @param newchanneldelay
     * @param newglobalLimitWrite
     * @param newglobalLimitRead
     * @param newglobaldelay
     */
    public void configure(long newchannelLimitWrite,
            long newchannelLimitRead, long newchanneldelay,
            long newglobalLimitWrite, long newglobalLimitRead,
            long newglobaldelay) {
        this.channelLimitWrite = newchannelLimitWrite;
        this.channelLimitRead = newchannelLimitRead;
        this.channelDelay = newchanneldelay;
        this.globalLimitWrite = newglobalLimitWrite;
        this.globalLimitRead = newglobalLimitRead;
        this.globalDelay = newglobaldelay;
        if (this.globalTrafficMonitor != null) {
            this.globalTrafficMonitor.configure(null,
                    newglobalLimitWrite, newglobalLimitRead, newglobaldelay);
        }
    }

    /**
     * @return the Global TrafficCounter or null if this support is disabled
     */
    public TrafficCounter getGlobalTrafficCounter() {
        if (this.globalActive) {
            if (this.globalTrafficMonitor == null) {
                this.globalTrafficMonitor = new TrafficCounter(this,
                        this.executorService, null, "GlobalPC",
                        this.globalLimitWrite, this.globalLimitRead,
                        this.globalDelay);
                this.globalTrafficMonitor.start();
            }
        }
        return this.globalTrafficMonitor;
    }

    /**
     * @param channel
     * @return the channel TrafficCounter or null if this support is
     *         disabled
     */
    public TrafficCounter createChannelTrafficCounter(Channel channel) {
        if (this.channelActive && ((this.channelLimitRead > 0) || (this.channelLimitWrite > 0)
                || (this.channelDelay > 0))) {
            return new TrafficCounter(this, this.executorService, channel,
                    "ChannelPC" + channel.getId(), this.channelLimitWrite,
                    this.channelLimitRead, this.channelDelay);
        }
        return null;
    }

    /**
     * Stop the global TrafficCounter if any (Even it is stopped, the
     * factory can however be reused)
     *
     */
    public void stopGlobalTrafficCounter() {
        if (this.globalTrafficMonitor != null) {
            this.globalTrafficMonitor.stop();
            this.globalTrafficMonitor = null;
        }
    }

    /**
     * @return the channelDelay
     */
    public long getChannelDelay() {
        return this.channelDelay;
    }

    /**
     * @param channelDelay
     *            the channelDelay to set
     */
    public void setChannelDelay(long channelDelay) {
        this.channelDelay = channelDelay;
    }

    /**
     * @return the channelLimitRead
     */
    public long getChannelLimitRead() {
        return this.channelLimitRead;
    }

    /**
     * @param channelLimitRead
     *            the channelLimitRead to set
     */
    public void setChannelLimitRead(long channelLimitRead) {
        this.channelLimitRead = channelLimitRead;
    }

    /**
     * @return the channelLimitWrite
     */
    public long getChannelLimitWrite() {
        return this.channelLimitWrite;
    }

    /**
     * @param channelLimitWrite
     *            the channelLimitWrite to set
     */
    public void setChannelLimitWrite(long channelLimitWrite) {
        this.channelLimitWrite = channelLimitWrite;
    }

    /**
     * @return the globalDelay
     */
    public long getGlobalDelay() {
        return this.globalDelay;
    }

    /**
     * @param globalDelay
     *            the globalDelay to set
     */
    public void setGlobalDelay(long globalDelay) {
        this.globalDelay = globalDelay;
        if (this.globalTrafficMonitor != null) {
            this.globalTrafficMonitor.configure(null,
                    this.globalLimitWrite, this.globalLimitRead,
                    this.globalDelay);
        }
    }

    /**
     * @return the globalLimitRead
     */
    public long getGlobalLimitRead() {
        return this.globalLimitRead;
    }

    /**
     * @param globalLimitRead
     *            the globalLimitRead to set
     */
    public void setGlobalLimitRead(long globalLimitRead) {
        this.globalLimitRead = globalLimitRead;
        if (this.globalTrafficMonitor != null) {
            this.globalTrafficMonitor.configure(null,
                    this.globalLimitWrite, this.globalLimitRead,
                    this.globalDelay);
        }
    }

    /**
     * @return the globalLimitWrite
     */
    public long getGlobalLimitWrite() {
        return this.globalLimitWrite;
    }

    /**
     * @param globalLimitWrite
     *            the globalLimitWrite to set
     */
    public void setGlobalLimitWrite(long globalLimitWrite) {
        this.globalLimitWrite = globalLimitWrite;
        if (this.globalTrafficMonitor != null) {
            this.globalTrafficMonitor.configure(null,
                    this.globalLimitWrite, this.globalLimitRead,
                    this.globalDelay);
        }
    }

    /**
     * @return the channelActive
     */
    public boolean isChannelActive() {
        return this.channelActive;
    }

    /**
     * @return the globalActive
     */
    public boolean isGlobalActive() {
        return this.globalActive;
    }

}
