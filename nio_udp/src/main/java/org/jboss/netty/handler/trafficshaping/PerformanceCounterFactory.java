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
package org.jboss.netty.handler.trafficshaping;

import java.util.concurrent.ExecutorService;

import org.jboss.netty.channel.Channel;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Frederic Bregier (fredbregier@free.fr)
 * @version $Rev$, $Date$
 *
 * The {@link PerformanceCounterFactory} is a Factory for
 * {@link PerformanceCounter}. It stores the necessary information to enable
 * dynamic creation of {@link PerformanceCounter} inside the
 * {@link TrafficShapingHandler}.
 *
 *
 */
public abstract class PerformanceCounterFactory {
    /**
     * No limit
     */
    public static long NO_LIMIT = -1;

    /**
     * No statistics (if channel or global PerformanceCounter is a very long
     * time living, it can produce a excess of capacity, i.e. 2^64 bytes so 17
     * billions of billions of bytes).
     */
    public static long NO_STAT = -1;

    /**
     * Default delay between two checks: 1s
     */
    public static long DEFAULT_DELAY = 1000;

    /**
     * ExecutorService to associated to any PerformanceCounter
     */
    private ExecutorService executorService = null;

    /**
     * Limit in B/s to apply to write for all channel PerformanceCounter
     */
    private long channelLimitWrite = NO_LIMIT;

    /**
     * Limit in B/s to apply to read for all channel PerformanceCounter
     */
    private long channelLimitRead = NO_LIMIT;

    /**
     * Delay between two performance snapshots for channel
     */
    private long channelDelay = DEFAULT_DELAY; // default 1 s

    /**
     * Will the PerformanceCounter for Channel be active
     */
    private boolean channelActive = true;

    /**
     * Limit in B/s to apply to write for the global PerformanceCounter
     */
    private long globalLimitWrite = NO_LIMIT;

    /**
     * Limit in B/s to apply to read for the global PerformanceCounter
     */
    private long globalLimitRead = NO_LIMIT;

    /**
     * Delay between two performance snapshots for global
     */
    private long globalDelay = DEFAULT_DELAY; // default 1 s

    /**
     * Will the PerformanceCounter for Global be active
     */
    private boolean globalActive = true;

    /**
     * Global Monitor
     */
    private PerformanceCounter globalPerformanceMonitor = null;

    /**
     * Called each time the accounting is computed for the PerformanceCounters.
     * This method could be used for instance to implement real time accounting.
     *
     * @param counter
     *            the PerformanceCounter that computes its performance
     */
    protected abstract void accounting(PerformanceCounter counter);

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
     *            True if each channel will have a PerformanceCounter
     * @param channelLimitWrite
     *            NO_LIMIT or a limit in bytes/s
     * @param channelLimitRead
     *            NO_LIMIT or a limit in bytes/s
     * @param channelDelay
     *            The delay between two computations of performances for
     *            channels or NO_STAT if no stats are to be computed
     * @param globalActive
     *            True if global context will have one unique PerformanceCounter
     * @param globalLimitWrite
     *            NO_LIMIT or a limit in bytes/s
     * @param globalLimitRead
     *            NO_LIMIT or a limit in bytes/s
     * @param globalDelay
     *            The delay between two computations of performances for global
     *            context or NO_STAT if no stats are to be computed
     */
    public PerformanceCounterFactory(ExecutorService executorService,
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
     *            True if each channel will have a PerformanceCounter
     * @param channelLimitWrite
     *            NO_LIMIT or a limit in bytes/s
     * @param channelLimitRead
     *            NO_LIMIT or a limit in bytes/s
     * @param globalActive
     *            True if global context will have one unique PerformanceCounter
     * @param globalLimitWrite
     *            NO_LIMIT or a limit in bytes/s
     * @param globalLimitRead
     *            NO_LIMIT or a limit in bytes/s
     */
    public PerformanceCounterFactory(ExecutorService executorService,
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
     *            True if each channel will have a PerformanceCounter
     * @param globalActive
     *            True if global context will have one unique PerformanceCounter
     * @param globalLimitWrite
     *            NO_LIMIT or a limit in bytes/s
     * @param globalLimitRead
     *            NO_LIMIT or a limit in bytes/s
     * @param globalDelay
     *            The delay between two computations of performances for global
     *            context or NO_STAT if no stats are to be computed
     */
    public PerformanceCounterFactory(ExecutorService executorService,
            boolean channelActive, boolean globalActive, long globalLimitWrite,
            long globalLimitRead, long globalDelay) {
        init(executorService, channelActive, NO_LIMIT, NO_LIMIT,
                DEFAULT_DELAY, globalActive, globalLimitWrite, globalLimitRead,
                globalDelay);
    }

    /**
     * Constructor using NO_LIMIT for channels and default delay for all
     *
     * @param executorService
     *            created for instance like Executors.newCachedThreadPool
     * @param channelActive
     *            True if each channel will have a PerformanceCounter
     * @param globalActive
     *            True if global context will have one unique PerformanceCounter
     * @param globalLimitWrite
     *            NO_LIMIT or a limit in bytes/s
     * @param globalLimitRead
     *            NO_LIMIT or a limit in bytes/s
     */
    public PerformanceCounterFactory(ExecutorService executorService,
            boolean channelActive, boolean globalActive, long globalLimitWrite,
            long globalLimitRead) {
        init(executorService, channelActive, NO_LIMIT, NO_LIMIT,
                DEFAULT_DELAY, globalActive, globalLimitWrite, globalLimitRead,
                DEFAULT_DELAY);
    }

    /**
     * Constructor using NO_LIMIT and default delay for all
     *
     * @param executorService
     *            created for instance like Executors.newCachedThreadPool
     * @param channelActive
     *            True if each channel will have a PerformanceCounter
     * @param globalActive
     *            True if global context will have one unique PerformanceCounter
     */
    public PerformanceCounterFactory(ExecutorService executorService,
            boolean channelActive, boolean globalActive) {
        init(executorService, channelActive, NO_LIMIT, NO_LIMIT,
                DEFAULT_DELAY, globalActive, NO_LIMIT, NO_LIMIT, DEFAULT_DELAY);
    }

    /**
     * Enable to change the active status of PerformanceCounter on Channels (for
     * new one only)
     *
     * @param active
     */
    public void setChannelActive(boolean active) {
        this.channelActive = active;
    }

    /**
     * Enable to change the active status of PerformanceCounter on Global (stop
     * or start if necessary)
     *
     * @param active
     */
    public void setGlobalActive(boolean active) {
        if (this.globalActive) {
            if (!active) {
                stopGlobalPerformanceCounter();
            }
        }
        this.globalActive = active;
        getGlobalPerformanceCounter();
    }

    /**
     * Change the underlying limitations. Only Global PerformanceCounter (if
     * any) is dynamically changed, but Channels PerformanceCounters are not
     * changed, only new created ones.
     *
     * @param newchannelLimitWrite
     * @param newchannelLimitRead
     * @param newchanneldelay
     * @param newglobalLimitWrite
     * @param newglobalLimitRead
     * @param newglobaldelay
     */
    public void changeConfiguration(long newchannelLimitWrite,
            long newchannelLimitRead, long newchanneldelay,
            long newglobalLimitWrite, long newglobalLimitRead,
            long newglobaldelay) {
        this.channelLimitWrite = newchannelLimitWrite;
        this.channelLimitRead = newchannelLimitRead;
        this.channelDelay = newchanneldelay;
        this.globalLimitWrite = newglobalLimitWrite;
        this.globalLimitRead = newglobalLimitRead;
        this.globalDelay = newglobaldelay;
        if (this.globalPerformanceMonitor != null) {
            this.globalPerformanceMonitor.changeConfiguration(null,
                    newglobalLimitWrite, newglobalLimitRead, newglobaldelay);
        }
    }

    /**
     * @return the Global PerformanceCounter or null if this support is disabled
     */
    public PerformanceCounter getGlobalPerformanceCounter() {
        if (this.globalActive) {
            if (this.globalPerformanceMonitor == null) {
                this.globalPerformanceMonitor = new PerformanceCounter(this,
                        this.executorService, null, "GlobalPC",
                        this.globalLimitWrite, this.globalLimitRead,
                        this.globalDelay);
                this.globalPerformanceMonitor.startMonitoring();
            }
        }
        return this.globalPerformanceMonitor;
    }

    /**
     * @param channel
     * @return the channel PerformanceCounter or null if this support is
     *         disabled
     */
    public PerformanceCounter createChannelPerformanceCounter(Channel channel) {
        if (this.channelActive && ((this.channelLimitRead > NO_LIMIT) || (this.channelLimitWrite > NO_LIMIT)
                || (this.channelDelay > NO_STAT))) {
            return new PerformanceCounter(this, this.executorService, channel,
                    "ChannelPC" + channel.getId(), this.channelLimitWrite,
                    this.channelLimitRead, this.channelDelay);
        }
        return null;
    }

    /**
     * Stop the global performance counter if any (Even it is stopped, the
     * factory can however be reused)
     *
     */
    public void stopGlobalPerformanceCounter() {
        if (this.globalPerformanceMonitor != null) {
            this.globalPerformanceMonitor.stopMonitoring();
            this.globalPerformanceMonitor = null;
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
        if (this.globalPerformanceMonitor != null) {
            this.globalPerformanceMonitor.changeConfiguration(null,
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
        if (this.globalPerformanceMonitor != null) {
            this.globalPerformanceMonitor.changeConfiguration(null,
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
        if (this.globalPerformanceMonitor != null) {
            this.globalPerformanceMonitor.changeConfiguration(null,
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
