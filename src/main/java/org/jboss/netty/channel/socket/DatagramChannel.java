/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;

import org.jboss.netty.channel.Channel;

/**
 * A UDP/IP {@link Channel} which is created by {@link DatagramChannelFactory}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 * @apiviz.landmark
 * @apiviz.composedOf org.jboss.netty.channel.socket.DatagramChannelConfig
 */
public interface DatagramChannel extends Channel {
    DatagramChannelConfig getConfig();
    InetSocketAddress getLocalAddress();
    InetSocketAddress getRemoteAddress();

    /**
     * Joins a multicast group.
     */
    void joinGroup(InetAddress multicastAddress);

    /**
     * Joins the specified multicast group at the specified interface.
     */
    void joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface);

    /**
     * Leaves a multicast group.
     */
    void leaveGroup(InetAddress multicastAddress);

    /**
     * Leaves a multicast group on a specified local interface.
     */
    void leaveGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface);
}
