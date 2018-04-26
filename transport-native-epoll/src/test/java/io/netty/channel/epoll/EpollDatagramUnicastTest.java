/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.DatagramUnicastTest;

import java.util.List;

public class EpollDatagramUnicastTest extends DatagramUnicastTest {
    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> newFactories() {
        return EpollSocketTestPermutation.INSTANCE.datagram();
    }

    public void testSimpleSendWithConnect(Bootstrap sb, Bootstrap cb) throws Throwable {
        // Run this test with IP_RECVORIGDSTADDR option enabled
        sb.option(EpollChannelOption.IP_RECVORIGDSTADDR, true);
        super.testSimpleSendWithConnect(sb, cb);
        sb.option(EpollChannelOption.IP_RECVORIGDSTADDR, false);
    }
}
