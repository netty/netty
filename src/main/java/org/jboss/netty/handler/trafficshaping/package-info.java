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
 * be active or inactive (traffic or statistics).</P><br><br><br>
 *
 * <P>Three classes implement this behavior:<br><br>
 * <ul>
 * <li>) <tt>PerformanceCounter</tt>: this class is the kernel of the package. It can be accessed to get some extra information
 * like the read or write bytes since last check, the read and write bandwidth from last check...</li><br><br>
 *
 * <li>) <tt>PerformanceCounterFactory</tt>: this class has to be implemented in your code in order to implement (eventually empty)
 * the accounting method. This class is a Factory for PerformanceCounter which is used in the third class to create the
 * necessary PerformanceCounter according to your specifications.</li><br><br>
 *
 * <li>) <tt>TrafficShapingHandler</tt>: this class is the handler to be inserted in your pipeline. The insertion can be wherever
 * you want, but <b>it must be placed before any <tt>MemoryAwareThreadPoolExecutor</tt></b> in your pipeline.</li><br><br>
 * <b><i>It is really recommended
 * to have such a <tt>emoryAwareThreadPoolExecutor</tt> (either non ordered or <tt>OrderedMemoryAwareThreadPoolExecutor</tt>) in your pipeline</i></b>
 * when you want to use this feature with some real traffic shaping, since it will allow to relax the constraint on
 * NioWorker to do other jobs if necessary.<br>
 * Instead, if you don't, you can have the following situation: if there are more clients
 * connected and doing data transfer (either in read or write) than NioWorker, your global performance can be under your specifications or even
 * sometimes it will block for a while which can turn to "timeout" operations.
 * For instance, let says that you've got 2 NioWorkers, and 10 clients wants to send data to your server. If you set a bandwidth limitation
 * of 100KB/s for each channel (client), you could have a final limitation of about 60KB/s for each channel since NioWorkers are
 * stopping by this handler.<br><br>
 * The method <tt>getMessageSize(MessageEvent)</tt> has to be implemented to specify what is the size of the object to be read or write
 * accordingly to the type of object. In simple case, it can be as simple as a call to <tt>getChannelBufferMessageSize(MessageEvent)</tt>.<br>
 * </ul></P><br><br>
 *
 * <P>Standard use could be as follow:</P><br><br>
 *
 * <P>To activate or deactivate the traffic shaping, change the value corresponding to your desire as
 * [Global or per Channel] [Write or Read] Limitation in byte/s.</P><br>
 * <P><tt>PerformanceCounterFactory.NO_LIMIT</tt> (-1)
 * stands for no limitation, so the traffic shaping is deactivate (on what you specified).</P><br>
 * <P>You can either change those values with the method <tt>changeConfiguration</tt> in PerformanceCounterFactory or
 * directly from the PerformanceCounter method <tt>changeConfiguration</tt>.</P><br>
 * <br><br>
 *
 * <P>To activate or deactivate the statistics, you can adjust the delay to a low (not less than 200ms
 * for efficiency reasons) or a high value (let say 24H in ms is huge enough to not get the problem)
 * or even using <tt>PerformanceCounterFactory.NO_STAT</tt> (-1).</P><br>
 * <P>And if you don't want to do anything with this statistics, just implement an empty method for
 * <tt>PerformanceCounterFactory.accounting(PerformanceCounter)</tt>.</P><br>
 * <P>Again this can be changed either from PerformanceCounterFactory or directly in PerformanceCounter.</P><br><br><br>
 *
 * <P>You can also completely deactivate channel or global PerformanceCounter by setting the boolean to false
 * accordingly to your needs in the PerformanceCounterFactory. It will deactivate the global Monitor. For channels monitor,
 * it will prevent new monitors to be created (or reversely they will be created for newly connected channels).</P><br><br><br>
 *
 * <P>So in your application you will create your own PerformanceCounterFactory and setting the values to fit your needs.</P><br><br>
 * <tt>MyPerformanceCounterFactory myFactory = new MyPerformanceCounter(...);</tt><br><br><br>
 * <P>Then you can create your pipeline (using a PipelineFactory since each TrafficShapingHandler must be unique by channel) and adding this handler before
 * your MemoryAwareThreadPoolExecutor:</P><br><br>
 * <tt>pipeline.addLast("MyTrafficShaping",new MyTrafficShapingHandler(myFactory));</tt><br>
 * <tt>...</tt><br>
 * <tt>pipeline.addLast("MemoryExecutor",new ExecutionHandler(memoryAwareThreadPoolExecutor));</tt><br><br><br>
 *
 * <P>TrafficShapingHandler must be unique by channel but however it is still global due to
 * the PerformanceCounterFactcory that is shared between all handlers across the channels.</P><br><br>
 *
 *
 *
 * @apiviz.exclude ^java\.lang\.
 */
package org.jboss.netty.handler.trafficshaping;

