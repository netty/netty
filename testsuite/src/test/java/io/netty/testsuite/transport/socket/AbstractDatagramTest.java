/*
 * Copyright 2012 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.testsuite.transport.socket.SocketTestPermutation.Factory;
import io.netty.testsuite.util.TestUtils;
import io.netty.util.NetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map.Entry;

public abstract class AbstractDatagramTest {

    private static final List<Entry<Factory<Bootstrap>, Factory<Bootstrap>>> COMBO = SocketTestPermutation.datagram();

    @Rule
    public final TestName testName = new TestName();

    protected final InternalLogger logger = InternalLoggerFactory.getInstance(getClass());

    protected volatile Bootstrap sb;
    protected volatile Bootstrap cb;
    protected volatile InetSocketAddress addr;

    protected void run() throws Throwable {
        int i = 0;
        for (Entry<Factory<Bootstrap>, Factory<Bootstrap>> e: COMBO) {
            sb = e.getKey().newInstance();
            cb = e.getValue().newInstance();
            addr = new InetSocketAddress(
                    NetUtil.LOCALHOST, TestUtils.getFreePort());
            sb.localAddress(addr);
            cb.localAddress(0).remoteAddress(addr);

            logger.info(String.format(
                    "Running: %s %d of %d (%s + %s)", testName.getMethodName(), ++ i, COMBO.size(), sb, cb));
            try {
                Method m = getClass().getDeclaredMethod(
                        testName.getMethodName(), Bootstrap.class, Bootstrap.class);
                m.invoke(this, sb, cb);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        }
    }
}
