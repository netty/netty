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

/**
 * <h2>Monitoring support in Netty</h2>
 * <p>
 * <h3>Introduction</h3> <br/>
 * In this package, Netty provides a small framework for gathering statistics -
 * simple counters, gauges, histograms - on how various Netty components
 * perform. Note that this package focuses on <em>gathering</em> measurements.
 * The task of actually <em>aggregating</em> those measurements into meaningful
 * statistics is left to pluggable {@link io.netty.monitor.spi.MonitorProvider
 * MonitorProviders}.
 * </p>
 * <p>
 * <h3>Supported Monitors</h3> <br/>
 * In its current incarnation, Netty's monitoring framework supports
 * <ul>
 * <li>
 * {@link io.netty.monitor.CounterMonitor CounterMonitors} - increment and
 * decrement a {@code long} value, e.g. <em>total number of bytes received on a
 * {@link io.netty.channel.Channel Channel}</em>;</li>
 * <li>
 * {@link io.netty.monitor.ValueMonitor ValueMonitors} - record an arbitrary
 * datum's current value, e.g. <em>overall number of
 * {@link io.netty.channel.Channel Channels}</em>;</li>
 * <li>
 * {@link io.netty.monitor.ValueDistributionMonitor
 * ValueDistributionMonitors} - track a value's distribution, e.g.
 * <em>size in bytes of incoming PDUs</em>;</li>
 * <li>
 * {@link io.netty.monitor.EventRateMonitor EventRateMonitors} - track the
 * rate which an event occurs at, e.g. <em>incoming PDUs per second</em>.</li>
 * </ul>
 * </p>
 * <p>
 * <h3>Netty resources providing monitoring support</h3> <br/>
 * As of today, the following Netty resources offer monitoring support out of
 * the box:
 * <ul>
 * <li>
 * {@link io.netty.util.HashedWheelTimer HashedWheelTimer}
 * <ul>
 * <li>track distribution of deviation between scheduled and actual
 * {@link io.netty.util.Timeout Timeout} execution time, i.e. how accurate
 * {@code HashedWheelTimer} is;</li>
 * <li>track number of {@link io.netty.util.Timeout Timeout}s executed per
 * second, i.e. {@code HashedWheelTimer}'s throughput.</li>
 * </ul>
 * </li>
 * </ul>
 * This list is expected to expand over time.
 * </p>
 * <p>
 * <h3>Design</h3> <br/>
 * As a rule, Netty refrains from introducing dependencies on third-party
 * libraries as much as possible. When designing its monitoring support we
 * therefore faced the choice between either implementing our own fully-fledged
 * metrics subsystem, capable of efficiently computing histograms and related
 * statistics, or to provide an abstraction layer over third-party libraries.
 * Given (a) that writing an <em>efficient</em> - especially in terms of memory
 * consumption - statistics library is a non-trivial task and (b) that many
 * organizations standardize on a specific statistics library, integrating it
 * into their tool chain, we chose the latter option. Essentially, arbitrary
 * statistics libraries may be <em>plugged in</em>.
 * </p>
 * <p>
 * To that end, Netty's monitoring subsystem defines an
 * {@link io.netty.monitor.spi <em>SPI</em>} - <strong>S</strong>ervice
 * <strong>P</strong>rovider <strong>I</strong>nterface - that needs to be
 * implemented by each concrete statistics provider. Netty ships with a
 * {@link io.netty.monitor.yammer default implementation} based on the excellent
 * <a href="http://metrics.codahale.com">Yammer Metrics</a> library. Central to
 * this <em>SPI</em> is the interface
 * {@link io.netty.monitor.spi.MonitorRegistryFactory MonitorRegistryFactory},
 * an implementation of which has to be provided by each metrics provider. It is
 * responsible for creating a {@link io.netty.monitor.MonitorRegistry
 * MonitorRegistry}, your entry point into Netty's monitoring support.
 * </p>
 * <p>
 * <h3>Usage</h3> <br/>
 * When utilizing Netty's monitoring support, you need to obtain a reference to
 * a {@link io.netty.monitor.MonitorRegistry MonitorRegistry}. It is through
 * this that you - either directly or, in the case of e.g.
 * {@link io.netty.util.HashedWheelTimer HashedWheelTimer}, indirectly - create
 * one or several of the supported monitors, e.g.
 * {@link io.netty.monitor.ValueMonitor ValueMonitor}.
 * </p>
 * <p>
 * So how do you obtain a reference to a {@code MonitorRegistry}? In one of two
 * ways:
 * </p>
 * <p>
 * <h4>1. Using Java's Service Loader support</h4> <br/>
 * This approach works whenever our desired monitoring provider registers its
 * {@link io.netty.montior.spi.MonitorRegistryFactory MonitorRegistryFactory}
 * implementation in a file called
 * {@code META-INF/services/io.netty.monitor.spi.MonitorRegistryFactory} located
 * on the classpath, typically within a jar containing the implementation
 * proper. In this case Java 6's {@link java.util.ServiceLoader ServiceLoader}
 * will be able to instantiate that {@code MonitorRegistryFactory}, which in
 * turn creates your {@code MonitorRegistry}. It goes without saying that
 * Netty's {@code Yammer}-based default monitoring provider supports this
 * approach.
 * </p>
 * <p>
 * To ease this process Netty provides
 * {@link io.netty.monitor.MonitorRegistries MonitorRegistries}, a convenience
 * class that locates all {@code MonitorRegistryFactories} on the classpath and
 * which may be asked for a {@code MonitorRegistry}. In the standard case where
 * only one such provider exists, this amounts to:
 *
 * <pre>
 * final MonitorRegistry myMonitorRegistry =
 *                           MonitorRegistries.instance().{@link io.netty.monitor.MonitorRegistries#unique() unique()};
 * final {@link io.netty.monitor.ValueMonitor ValueMonitor} overallNumberOfChannels =
 *                           myMonitorRegistry.newValueDistributionMonitor(monitorName);
 * ...
 * </pre>
 *
 * This approach may be the most easy to use. As a downside, you relinquish
 * control over how to instantiate your {@code MonitorRegistry}. Some use cases
 * may not be amenable to this approach.
 * </p>
 * <p>
 * <h4>2. Directly instantiating a {@code MonitorRegistry}</h4> <br/>
 * Of course, nothing keeps you from directly instantiating your desired
 * {@code MonitorRegistry}, and this may well be the most flexible approach:
 *
 * <pre>
 * final MyMetricsProvider myMetricsProvider = new MyMetricsProvider();
 * final MonitorRegistry myMonitorRegistry = new MyGrandMonitorRegistry(myMetricsProvider);
 * final {@link io.netty.monitor.ValueMonitor ValueMonitor} overallNumberOfChannels =
 *                           myMonitorRegistry.newValueDistributionMonitor(monitorName);
 * ...
 * </pre>
 *
 * Obviously, instantiating your {@code MonitorRegistry} may thus be delegated
 * to your DI-container of choice, i.e. <a
 * href="http://www.springsource.org/spring-framework">Spring</a>, <a
 * href="http://code.google.com/p/google-guice/">Guice</a> or <a
 * href="http://seamframework.org/Weld">Weld</a>.
 * </p>
 * <p>
 * <strong>DISCLAIMER</strong> It should be noted that Netty's monitoring support
 * was heavily inspired by <a href="http://codahale.com/">Coda Hale's</a>
 * excellent <a href="http://metrics.codahale.com">Yammer Metrics</a> library.
 * </p>
 * @apiviz.hidden
 */
package io.netty.monitor;

