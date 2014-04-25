/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/**
 * Implementation of a Traffic Shaping Handler and Dynamic Statistics.
 *
 * <p>The main goal of this package is to allow you to shape the traffic (bandwidth limitation),
 * but also to get statistics on how many bytes are read or written. Both functions can
 * be active or inactive (traffic or statistics).</p>
 *
 * <p>Two classes implement this behavior:
 * <ul>
 * <li> <tt>{@link io.netty.handler.traffic.TrafficCounter}</tt>: this class implements the counters needed by the
 * handlers.  It can be accessed to get some extra information like the read or write bytes since last check,
 * the read and write bandwidth from last check...</li>
 *
 * <li> <tt>{@link io.netty.handler.traffic.AbstractTrafficShapingHandler}</tt>: this abstract class implements
 * the kernel of traffic shaping. It could be extended to fit your needs. Two classes are proposed as default
 * implementations: see {@link io.netty.handler.traffic.ChannelTrafficShapingHandler} and
 * {@link io.netty.handler.traffic.GlobalTrafficShapingHandler} respectively for Channel traffic shaping and
 * global traffic shaping.</li>
 * </ul></p>
 *
 * <p>Both inbound and outbound traffic can be shaped independently.  This is done by either passing in
 * the desired limiting values to the constructors of both the Channel and Global traffic shaping handlers,
 * or by calling the <tt>configure</tt> method on the {@link io.netty.handler.traffic.AbstractTrafficShapingHandler}.
 * A value of 0 for either parameter indicates that there should be no limitation.  This allows you to monitor the
 * incoming and outgoing traffic without shaping.</p>
 *
 * <p>To activate or deactivate the statistics, you can adjust the delay to a low (suggested not less than 200ms
 * for efficiency reasons) or a high value (let say 24H in millisecond is huge enough to not get the problem)
 * or even using <tt>0</tt> which means no computation will be done.</p>
 *
 * <p>If you want to do anything with these statistics, just override the <tt>doAccounting</tt> method.<br>
 * This interval can be changed either from the method <tt>configure</tt>
 * in {@link io.netty.handler.traffic.AbstractTrafficShapingHandler} or directly using the method <tt>configure</tt>
 * of {@link io.netty.handler.traffic.TrafficCounter}.</p>
 *
 * <p>Note that a new {@link io.netty.handler.traffic.ChannelTrafficShapingHandler} must be created
 * for each new channel, but only one {@link io.netty.handler.traffic.GlobalTrafficShapingHandler} must be created
 * for all channels.</p>
 *
 * <p>Note also that you can create different GlobalTrafficShapingHandler if you want to separate classes of
 * channels (for instance either from business point of view or from bind address point of view).</p>
 */
package io.netty.handler.traffic;

