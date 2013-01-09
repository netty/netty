package io.netty.monitor;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import io.netty.monitor.support.SampleMonitorRegistryFactory;

import org.junit.Test;

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

		final MonitorRegistry registry = objectUnderTest
				.forProvider(SampleMonitorRegistryFactory.PROVIDER);

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
