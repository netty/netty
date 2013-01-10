/*
 * Copyright 2013 The Netty Project
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

package io.netty.monitor;

import io.netty.monitor.support.SampleMonitorRegistryFactory;
import org.junit.Test;

import static org.junit.Assert.*;

public class MonitorRegistriesTest {

    @Test
    public final void instanceShouldNotReturnNull() {
        assertNotNull("instance() should NEVER return null",
                MonitorRegistries.instance());
    }

    @Test
    public final void instanceShouldAlwaysReturnTheSameInstance() {
        final MonitorRegistries firstInstance = MonitorRegistries.instance();
        final MonitorRegistries secondInstance = MonitorRegistries.instance();
        assertSame("instance() should always return the same instance",
                firstInstance, secondInstance);
    }

    @Test
    public final void forProviderShouldReturnMonitorRegistryMatchingTheSuppliedProvider() {
        final MonitorRegistries objectUnderTest = MonitorRegistries.instance();

        final MonitorRegistry registry = MonitorRegistries.forProvider(SampleMonitorRegistryFactory.PROVIDER);

        assertSame("forProvider(" + SampleMonitorRegistryFactory.PROVIDER
                + ") should return a MonitorRegistry by the supplied provider",
                SampleMonitorRegistryFactory.SampleMonitorRegistry.class,
                registry.getClass());
    }

    @Test
    public final void uniqueShouldThrowIllegalStateExceptionIfMoreThanOneProviderIsRegistered() {
        final MonitorRegistries objectUnderTest = MonitorRegistries.instance();

        objectUnderTest.unique();
    }
}
