/*
 * Copyright 2023 The Netty Project
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
package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ServerChannel;

/**
 * A channel initializer extension make it possible to enforce rules and apply modifications across multiple,
 * disconnected uses of Netty within the same JVM process.
 * <p>
 * For instance, application-level firewall rules can be injected into all uses of Netty within an application,
 * without making changes to such uses that are otherwise outside the purview of the application code,
 * such as 3rd-party libraries.
 * <p>
 * Channel initializer extensions are <em>not</em> enabled by default, because of their power to influence Netty
 * pipelines across libraries, frameworks, and use-cases.
 * Extensions must be explicitly enabled by setting the {@value #EXTENSIONS_SYSTEM_PROPERTY} to {@code serviceload}.
 * <p>
 * All channel initializer extensions that are available on the classpath will be
 * {@linkplain java.util.ServiceLoader#load(Class) service-loaded} and used by all {@link AbstractBootstrap} subclasses.
 * <p>
 * Note that this feature will not work for Netty uses that are shaded <em>and relocated</em> into other libraries.
 * The classes in a relocated Netty library are technically distinct and incompatible types. This means the
 * service-loader in non-relocated Netty will not see types from a relocated Netty, and vice versa.
 */
public abstract class ChannelInitializerExtension {
    /**
     * The name of the system property that control initializer extensions.
     * <p>
     * These extensions can potentially be a security liability, so they are disabled by default.
     * <p>
     * To enable the extensions, application operators can explicitly opt in by setting this system property to the
     * value {@code serviceload}. This will enable all the extensions that are available through the service loader
     * mechanism.
     * <p>
     * To load and log (at INFO level) all available extensions without actually running them, set this system property
     * to the value {@code log}.
     */
    public static final String EXTENSIONS_SYSTEM_PROPERTY = "io.netty.bootstrap.extensions";

    /**
     * Get the "priority" of this extension. If multiple extensions are avilable, then they will be called in their
     * priority order, from lowest to highest.
     * <p>
     * Implementers are encouraged to pick a number between {@code -100.0} and {@code 100.0}, where extensions that have
     * no particular opinion on their priority are encouraged to return {@code 0.0}.
     * <p>
     * Extensions with lower priority will get called first, while extensions with greater priority may be able to
     * observe the effects of extensions with lesser priority.
     * <p>
     * Note that if multiple extensions have the same priority, then their relative order will be unpredictable.
     * As such, implementations should always take into consideration that other extensions might be called before
     * or after them.
     * <p>
     * Override this method to specify your own priority.
     * The default implementation just returns {@code 0}.
     *
     * @return The priority.
     */
    public double priority() {
        return 0;
    }

    /**
     * Called by {@link Bootstrap} after the initialization of the given client channel.
     * <p>
     * The method is allowed to modify the handlers in the pipeline, the channel attributes, or the channel options.
     * The method must refrain from doing any I/O, or from closing the channel.
     * <p>
     * Override this method to add your own callback logic.
     * The default implementation does nothing.
     *
     * @param channel The channel that was initialized.
     */
    public void postInitializeClientChannel(Channel channel) {
    }

    /**
     * Called by {@link ServerBootstrap} after the initialization of the given server listener channel.
     * The listener channel is responsible for invoking the {@code accept(2)} system call,
     * and for producing child channels.
     * <p>
     * The method is allowed to modify the handlers in the pipeline, the channel attributes, or the channel options.
     * The method must refrain from doing any I/O, or from closing the channel.
     * <p>
     * Override this method to add your own callback logic.
     * The default implementation does nothing.
     *
     * @param channel The channel that was initialized.
     */
    public void postInitializeServerListenerChannel(ServerChannel channel) {
    }

    /**
     * Called by {@link ServerBootstrap} after the initialization of the given child channel.
     * A child channel is a newly established connection from a client to the server.
     * <p>
     * The method is allowed to modify the handlers in the pipeline, the channel attributes, or the channel options.
     * The method must refrain from doing any I/O, or from closing the channel.
     * <p>
     * Override this method to add your own callback logic.
     * The default implementation does nothing.
     *
     * @param channel The channel that was initialized.
     */
    public void postInitializeServerChildChannel(Channel channel) {
    }
}
