/*
 * Copyright 2025 The Netty Project
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
package io.netty5.handler.ssl.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.security.Provider;
import java.security.Security;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated("Provider value stored in the BouncyCastlePemReader#bcProvider field is also used by other tests")
public class BouncyCastleUtilTest {

    @Test
    public void testBouncyCastleProviderLoaded() {
        // tests org.bouncycastle.jce.provider.BouncyCastleProvider is detected as available
        // because provider with matching name is present in 'java.security.Security'

        assertTrue(BouncyCastleUtil.isBcProvAvailable());
        assertTrue(BouncyCastleUtil.isBcPkixAvailable());
        assertTrue(BouncyCastleUtil.isBcTlsAvailable());
        Provider bcProvider = BouncyCastleUtil.getBcProviderJce();
        assertNotNull(bcProvider);
        assertNotNull(BouncyCastleUtil.getBcProviderJsse());
        BouncyCastleUtil.reset();

        Provider bouncyCastleProvider = new BouncyCastleProvider();
        Security.addProvider(bouncyCastleProvider);
        assertTrue(BouncyCastleUtil.isBcProvAvailable());
        bcProvider = BouncyCastleUtil.getBcProviderJce();
        assertSame(bouncyCastleProvider, bcProvider);
        Security.removeProvider(bouncyCastleProvider.getName());
        BouncyCastleUtil.reset();
    }

    @Test
    public void testBouncyCastleFipsProviderLoaded() {
        // tests org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider is detected as available
        // because provider with matching name is present in 'java.security.Security'

        assertTrue(BouncyCastleUtil.isBcProvAvailable());
        Provider bcProvider = BouncyCastleUtil.getBcProviderJce();
        assertInstanceOf(BouncyCastleProvider.class, bcProvider);
        BouncyCastleUtil.reset();

        // we don't expect to have both BC and BCFIPS available, but BouncyCastleProvider is on the classpath
        // hence we need to add a fake BouncyCastleFipsProvider provider
        Provider fakeBouncyCastleFipsProvider = new Provider("BCFIPS", 1.000205,
                "BouncyCastle Security Provider (FIPS edition) v1.0.2.5") { };
        Security.addProvider(fakeBouncyCastleFipsProvider);
        assertTrue(BouncyCastleUtil.isBcProvAvailable());
        bcProvider = BouncyCastleUtil.getBcProviderJce();
        assertSame(fakeBouncyCastleFipsProvider, bcProvider);
        Security.removeProvider(fakeBouncyCastleFipsProvider.getName());
        BouncyCastleUtil.reset();
    }
}
