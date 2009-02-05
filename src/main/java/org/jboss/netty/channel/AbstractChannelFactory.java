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
package org.jboss.netty.channel;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public abstract class AbstractChannelFactory implements ChannelFactory {

    private final InternalLogger logger = InternalLoggerFactory.getInstance(getClass());

    private final Object trafficMonitorLock = new Object();
    private volatile TrafficMonitor[] trafficMonitors = new TrafficMonitor[0];

    protected AbstractChannelFactory() {
        super();
    }

    public void addTrafficMonitor(TrafficMonitor monitor) {
        if (monitor == null) {
            throw new NullPointerException("monitor");
        }

        synchronized (trafficMonitorLock) {
            final TrafficMonitor[] oldTrafficMonitors = trafficMonitors;
            for (TrafficMonitor m: oldTrafficMonitors) {
                if (m == monitor) {
                    return;
                }
            }
            final TrafficMonitor newTrafficMonitors[] =
                new TrafficMonitor[oldTrafficMonitors.length + 1];
            System.arraycopy(oldTrafficMonitors, 0, newTrafficMonitors, 0, oldTrafficMonitors.length);
            newTrafficMonitors[oldTrafficMonitors.length] = monitor;

            trafficMonitors = newTrafficMonitors;
        }
    }

    public void removeTrafficMonitor(TrafficMonitor monitor) {
        if (monitor == null) {
            throw new NullPointerException("monitor");
        }

        synchronized (trafficMonitorLock) {
            final TrafficMonitor[] oldTrafficMonitors = trafficMonitors;
            boolean found = false;
            for (TrafficMonitor m: oldTrafficMonitors) {
                if (m == monitor) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return;
            }

            final TrafficMonitor newTrafficMonitors[] =
                new TrafficMonitor[oldTrafficMonitors.length - 1];

            int i = 0;
            for (TrafficMonitor m: oldTrafficMonitors) {
                if (m != monitor) {
                    newTrafficMonitors[i ++] = m;
                }
            }

            trafficMonitors = newTrafficMonitors;
        }
    }

    void fireChannelOpen(Channel channel) {
        TrafficMonitor[] trafficMonitors = this.trafficMonitors;
        for (TrafficMonitor m: trafficMonitors) {
            try {
                m.channelOpen(channel);
            } catch (Exception e) {
                logger.warn(
                        "An exception was thrown by " +
                        TrafficMonitor.class.getSimpleName() +
                        ".channelOpen().", e);
            }
        }
    }

    void fireChannelClosed(Channel channel) {
        TrafficMonitor[] trafficMonitors = this.trafficMonitors;
        for (TrafficMonitor m: trafficMonitors) {
            try {
                m.channelClosed(channel);
            } catch (Exception e) {
                logger.warn(
                        "An exception was thrown by " +
                        TrafficMonitor.class.getSimpleName() +
                        ".channelClosed().", e);
            }
        }
    }

    void fireChannelRead(Channel channel, int amount) {
        TrafficMonitor[] trafficMonitors = this.trafficMonitors;
        for (TrafficMonitor m: trafficMonitors) {
            try {
                m.channelRead(channel, amount);
            } catch (Exception e) {
                logger.warn(
                        "An exception was thrown by " +
                        TrafficMonitor.class.getSimpleName() +
                        ".channelRead().", e);
            }
        }
    }

    void fireChannelWriteScheduled(Channel channel, int amount) {
        TrafficMonitor[] trafficMonitors = this.trafficMonitors;
        for (TrafficMonitor m: trafficMonitors) {
            try {
                m.channelWriteScheduled(channel, amount);
            } catch (Exception e) {
                logger.warn(
                        "An exception was thrown by " +
                        TrafficMonitor.class.getSimpleName() +
                        ".channelWriteScheduled().", e);
            }
        }
    }

    void fireChannelWritten(Channel channel, int amount) {
        TrafficMonitor[] trafficMonitors = this.trafficMonitors;
        for (TrafficMonitor m: trafficMonitors) {
            try {
                m.channelWritten(channel, amount);
            } catch (Exception e) {
                logger.warn(
                        "An exception was thrown by " +
                        TrafficMonitor.class.getSimpleName() +
                        ".channelWritten().", e);
            }
        }
    }
}
