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
package org.jboss.netty.channel.socket.nio;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.util.DummyHandler;
import org.junit.Test;

/**
 * Tests if Netty works around the infamous
 * <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6403933">'spinning selector' bug</a>.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Daniel Bevenius (dbevenius@jboss.com)
 * @version $Rev$, $Date$
 */
public class SpinningSelectorBugTest {

    @Test(timeout = 10000)
    public void test() throws Exception {
        ServerSocket ss = new ServerSocket(0);

        ClientBootstrap cb = new ClientBootstrap(new NioClientSocketChannelFactory(
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
        cb.getPipeline().addLast("dummy", new DummyHandler());
        ChannelFuture cf = cb.connect(new InetSocketAddress("127.0.0.1", ss.getLocalPort()));
        Socket s = ss.accept();
        cf.awaitUninterruptibly();
        // TODO cf.ensureSuccess();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // Ignore
        }

        // Send RST to trigger selector to spin.
        s.setSoLinger(true, 0);
        s.close();

        // If selector spins, the client side selector will not notice the closure.
        cf.getChannel().getCloseFuture().awaitUninterruptibly();

        cb.releaseExternalResources();
    }
}
