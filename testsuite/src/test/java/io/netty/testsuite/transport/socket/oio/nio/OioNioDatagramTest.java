/*
 * Copyright 2011 The Netty Project
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
package io.netty.testsuite.transport.socket.oio.nio;

import java.util.concurrent.Executor;

import io.netty.channel.socket.DatagramChannelFactory;
import io.netty.channel.socket.nio.NioDatagramChannelFactory;
import io.netty.channel.socket.oio.OioDatagramChannelFactory;
import io.netty.testsuite.transport.socket.AbstractDatagramTest;

public class OioNioDatagramTest extends AbstractDatagramTest{

    @Override
    protected DatagramChannelFactory newServerSocketChannelFactory(Executor executor) {
        return new NioDatagramChannelFactory(executor);
    }

    @Override
    protected DatagramChannelFactory newClientSocketChannelFactory(Executor executor) {
        return new OioDatagramChannelFactory(executor);
    }

}
