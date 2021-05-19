/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import org.junit.BeforeClass;

import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeTrue;

public class OpenSslRenegotiateTest extends RenegotiateTest {

    @BeforeClass
    public static void checkOpenSsl() {
        OpenSsl.ensureAvailability();
    }

    @Override
    protected SslProvider serverSslProvider() {
        return SslProvider.OPENSSL;
    }

    protected void verifyResult(AtomicReference<Throwable> error) throws Throwable {
        Throwable cause = error.get();
        // Renegotation is not supported by the OpenSslEngine.
        assertThat(cause, is(instanceOf(SSLException.class)));
    }
}
