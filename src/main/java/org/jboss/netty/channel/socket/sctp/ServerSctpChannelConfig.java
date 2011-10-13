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
package org.jboss.netty.channel.socket.sctp;

import com.sun.nio.sctp.SctpStandardSocketOption;
import org.jboss.netty.channel.ChannelConfig;

/**
 * A {@link org.jboss.netty.channel.ChannelConfig} for a {@link org.jboss.netty.channel.socket.sctp.ServerSctpChannelConfig}.
 * <p/>
 * <h3>Available options</h3>
 * <p/>
 * In addition to the options provided by {@link org.jboss.netty.channel.ChannelConfig},
 * {@link org.jboss.netty.channel.socket.sctp.ServerSctpChannelConfig} allows the following options in the
 * option map:
 * <p/>
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@code "backlog"}</td><td>{@link #setBacklog(int)}</td>
 * </tr><tr>
 * <td>{@code "sctpInitMaxStreams"}</td><td>{@link #setInitMaxStreams(com.sun.nio.sctp.SctpStandardSocketOption.InitMaxStreams)} (int)}}</td>
 * </tr>
 * </table>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://github.com/jestan">Jestan Nirojan</a>
 *
 * @version $Rev$, $Date$
 */
public interface ServerSctpChannelConfig extends ChannelConfig {

    /**
     * Gets the backlog value to specify when the channel binds to a local
     * address.
     */
    int getBacklog();

    /**
     * Sets the backlog value to specify when the channel binds to a local
     * address.
     */
    void setBacklog(int backlog);


    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">{@code SCTP_INIT_MAXSTREAMS}</a> option.
     */
    SctpStandardSocketOption.InitMaxStreams getInitMaxStreams();

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">{@code SCTP_INIT_MAXSTREAMS}</a> option.
     */
    void setInitMaxStreams(SctpStandardSocketOption.InitMaxStreams initMaxStreams);
}
