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

package org.jboss.netty.handler.ssl;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioServerSocketChannelFactory;
import org.jboss.netty.handler.ssl.util.SelfSignedCertificate;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(Parameterized.class)
public abstract class SslTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SslTest.class);
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private static final File CERT_FILE;
    private static final File KEY_FILE;

    static {
        SelfSignedCertificate ssc;
        try {
            ssc = new SelfSignedCertificate();
        } catch (CertificateException e) {
            throw new Error(e);
        }
        CERT_FILE = ssc.certificate();
        KEY_FILE = ssc.privateKey();
    }

    @Parameters(name =
            "{index}: serverCtx = {0}, clientCtx = {1}, serverChannelFactory = {2}, clientChannelFactory = {3}")
    public static Collection<Object[]> params() throws Exception {
        // Populate the permutations.
        List<Object[]> params = new ArrayList<Object[]>();

        List<SslContext> serverContexts = new ArrayList<SslContext>();
        serverContexts.add(new JdkSslServerContext(CERT_FILE, KEY_FILE));

        List<SslContext> clientContexts = new ArrayList<SslContext>();
        clientContexts.add(new JdkSslClientContext(CERT_FILE));

        List<ChannelFactory> serverChannelFactories = new ArrayList<ChannelFactory>();
        serverChannelFactories.add(new NioServerSocketChannelFactory(executor, executor));
        serverChannelFactories.add(new OioServerSocketChannelFactory(executor, executor));

        List<ChannelFactory> clientChannelFactories = new ArrayList<ChannelFactory>();
        clientChannelFactories.add(new NioClientSocketChannelFactory(executor, executor));
        clientChannelFactories.add(new OioClientSocketChannelFactory(executor));

        boolean hasOpenSsl = OpenSsl.isAvailable();
        if (hasOpenSsl) {
            serverContexts.add(new OpenSslServerContext(null, CERT_FILE, KEY_FILE, null, null, null, 0, 0));

            // TODO: Client mode is not supported yet.
            // clientContexts.add(new OpenSslContext(null, CERT_FILE, null, null, null, 0, 0));
        } else {
            logger.warn("OpenSSL is unavailable and thus will not be tested.", OpenSsl.unavailabilityCause());
        }

        for (SslContext sctx: serverContexts) {
            for (SslContext cctx: clientContexts) {
                for (ChannelFactory scf: serverChannelFactories) {
                    for (ChannelFactory ccf: clientChannelFactories) {
                        if (scf instanceof OioServerSocketChannelFactory &&
                            ccf instanceof OioClientSocketChannelFactory) {
                            continue;
                        }

                        params.add(new Object[] { sctx, cctx, scf, ccf });
                    }
                }
            }
        }

        return params;
    }

    protected final SslContext serverCtx;
    protected final SslContext clientCtx;
    protected final ChannelFactory serverChannelFactory;
    protected final ChannelFactory clientChannelFactory;

    protected SslTest(
            SslContext serverCtx, SslContext clientCtx,
            ChannelFactory serverChannelFactory, ChannelFactory clientChannelFactory) {
        this.serverCtx = serverCtx;
        this.clientCtx = clientCtx;
        this.serverChannelFactory = serverChannelFactory;
        this.clientChannelFactory = clientChannelFactory;
    }
}
