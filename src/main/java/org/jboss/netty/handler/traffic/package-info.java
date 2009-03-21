/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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

/**
 * Implementation of a Traffic Shaping Handler and Dynamic Statistics.<br>
 * <br><br>
 *
 *
 * <P>The main goal of this package is to allow to shape the traffic (bandwidth limitation),
 * but also to get statistics on how many bytes are read or written. Both functions can
 * be active or inactive (traffic or statistics).</P>
 *
 * <P>Three classes implement this behavior:<br>
 * <ul>
 * <li> <tt>{@link TrafficCounter}</tt>: this class is the kernel of the package. It can be accessed to get some extra information
 * like the read or write bytes since last check, the read and write bandwidth from last check...</li><br><br>
 *
 * <li> <tt>{@link TrafficCounterFactory}</tt>: this class has to be implemented in your code in order to implement (eventually empty)
 * the accounting method. This class is a Factory for TrafficCounter which is used in the third class to create the
 * necessary TrafficCounter according to your specifications.</li><br><br>
 *
 * <li> <tt>{@link TrafficShapingHandler}</tt>: this class is the handler to be inserted in your pipeline. The insertion can be wherever
 * you want, but <b>it must be placed before any <tt>{@link MemoryAwareThreadPoolExecutor}</tt> in your pipeline</b>.</li><br>
 * <b><i>It is really recommended
 * to have such a</i> <tt>{@link MemoryAwareThreadPoolExecutor}</tt> <i>(either non ordered or </i>
 * <tt>{@link OrderedMemoryAwareThreadPoolExecutor}</tt>
 * <i>) in your pipeline</i></b>
 * when you want to use this feature with some real traffic shaping, since it will allow to relax the constraint on
 * NioWorker to do other jobs if necessary.<br>
 * Instead, if you don't, you can have the following situation: if there are more clients
 * connected and doing data transfer (either in read or write) than NioWorker, your global performance can be under your specifications or even
 * sometimes it will block for a while which can turn to "timeout" operations.
 * For instance, let says that you've got 2 NioWorkers, and 10 clients wants to send data to your server. If you set a bandwidth limitation
 * of 100KB/s for each channel (client), you could have a final limitation of about 60KB/s for each channel since NioWorkers are
 * stopping by this handler.<br>
 * When it is used as a read traffic shaper, the handler will set the channel as not readable, so as to relax the NioWorkers.<br><br>
 *  An {@link ObjectSizeEstimator} can be passed at construction to specify what
 * is the size of the object to be read or write accordingly to the type of
 * object. If not specified, it will used the {@link DefaultObjectSizeEstimator} implementation.
 * </ul></P>
 *
 * <P>Standard use could be as follow:</P>
 *
 * <P><ul>
 * <li>To activate or deactivate the traffic shaping, change the value corresponding to your desire as
 * [Global or per Channel] [Write or Read] Limitation in byte/s.</li><br>
 * A value of <tt>0</tt>
 * stands for no limitation, so the traffic shaping is deactivate (on what you specified).<br>
 * You can either change those values with the method <tt>changeConfiguration</tt> in TrafficCounterFactory or
 * directly from the TrafficCounter method <tt>changeConfiguration</tt>.<br>
 * <br>
 *
 * <li>To activate or deactivate the statistics, you can adjust the delay to a low (not less than 200ms
 * for efficiency reasons) or a high value (let say 24H in ms is huge enough to not get the problem)
 * or even using <tt>0</tt> which means no computation will be done.</li><br>
 * And if you don't want to do anything with this statistics, just implement an empty method for
 * <tt>TrafficCounterFactory.accounting(TrafficCounter)</tt>.<br>
 * Again this can be changed either from TrafficCounterFactory or directly in TrafficCounter.<br><br>
 *
 * <li>You can also completely deactivate channel or global TrafficCounter by setting the boolean to false
 * accordingly to your needs in the TrafficCounterFactory. It will deactivate the global Monitor. For channels monitor,
 * it will prevent new monitors to be created (or reversely they will be created for newly connected channels).</li>
 * </ul></P><br><br>
 *
 * <P>So in your application you will create your own TrafficCounterFactory and setting the values to fit your needs.</P>
 * <tt>MyTrafficCounterFactory myFactory = new MyTrafficCounter(...);</tt><br><br><br>
 * <P>Then you can create your pipeline (using a PipelineFactory since each TrafficShapingHandler must be unique by channel) and adding this handler before
 * your MemoryAwareThreadPoolExecutor:</P>
 * <tt>pipeline.addLast("MyTrafficShaping",new MyTrafficShapingHandler(myFactory));</tt><br>
 * <tt>...</tt><br>
 * <tt>pipeline.addLast("MemoryExecutor",new ExecutionHandler(memoryAwareThreadPoolExecutor));</tt><br><br>
 *
 * <P>TrafficShapingHandler must be unique by channel but however it is still global due to
 * the TrafficCounterFactcory that is shared between all handlers across the channels.</P>
 *
 *
 *
 * @apiviz.exclude ^java\.lang\.
 */
package org.jboss.netty.handler.traffic;

import org.jboss.netty.handler.execution.DefaultObjectSizeEstimator;
import org.jboss.netty.handler.execution.ObjectSizeEstimator;
import org.jboss.netty.handler.execution.MemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
