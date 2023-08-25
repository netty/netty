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
 * All channel initializer extensions that are available on the classpath will be
 * {@linkplain java.util.ServiceLoader#load(Class) service-loaded} and used by all {@link AbstractBootstrap} subclasses,
 * unless the extensions are explicitly disabled on the given bootstrap instance with a call to
 * {@link AbstractBootstrap#disableChannelInitializerExtensions()}.
 */
public interface ChannelInitializerExtension {
    /**
     * Inspect the information in the given {@link ApplicableInfo} object, and determine if this extension is applicable
     * for the given context.
     * <p>
     * If the extension is not applicable, then it won't be called.
     * <p>
     * This method may be called multiple times with different parameters for different contexts.
     *
     * @param info An object carrying information about the context of where and how this extension would be used.
     * @return {@code true} if this extension is interested in getting called in the given context,
     * otherwise {@code false}.
     */
    boolean isApplicable(ApplicableInfo info);

    /**
     * Get the "priority" of this extension. If multiple extensions are
     * {@linkplain #isApplicable(ApplicableInfo) applicable} to a given context, then they will be called in their
     * priority order, from lowest to highest.
     * <p>
     * Implementers are encouraged to pick a number between {@code -100.0} and {@code 100.0}, where extensions that have
     * no particular opinion on their priority are encouraged to return {@code 0.0}.
     * <p>
     * Extensions with lower priority will get called first, while extensions with greater priority may be able to
     * observe the effects of extensions with lesser priority.
     *
     * @return The priority.
     */
    double priority();

    /**
     * Called by {@link Bootstrap} after the initialization of the given client channel.
     * <p>
     * The method is allowed to modify the handlers in the pipeline, the channel attributes, or the channel options.
     * The method must refrain from doing any I/O, or from closing the channel.
     *
     * @param channel The channel that was initialized.
     */
    void postInitializeClientChannel(Channel channel);

    /**
     * Called by {@link ServerBootstrap} after the initialization of the given server listener channel.
     * The listener channel is responsible for invoking the {@code accept(2)} system call,
     * and for producing child channels.
     * <p>
     * The method is allowed to modify the handlers in the pipeline, the channel attributes, or the channel options.
     * The method must refrain from doing any I/O, or from closing the channel.
     *
     * @param channel The channel that was initialized.
     */
    void postInitializeServerListenerChannel(ServerChannel channel);

    /**
     * Called by {@link ServerBootstrap} after the initialization of the given child channel.
     * A child channel is a newly established connection from a client to the server.
     * <p>
     * The method is allowed to modify the handlers in the pipeline, the channel attributes, or the channel options.
     * The method must refrain from doing any I/O, or from closing the channel.
     *
     * @param channel The channel that was initialized.
     */
    void postInitializeServerChildChannel(Channel channel);

    /**
     * Provides information about the context where an extension might be used.
     * Extensions are given instances of this class through the {@link #isApplicable(ApplicableInfo)} method.
     * <p>
     * This class is a parameter-object, and is {@code final} so that additional information can be made available in
     * the future, without breaking backwards compatibility.
     */
    final class ApplicableInfo {
        private final Class<?> bootstrapClass;

        ApplicableInfo(Class<?> bootstrapClass) {
            this.bootstrapClass = bootstrapClass;
        }

        /**
         * Get the concrete {@link AbstractBootstrap} subclass that is interested in using this extension.
         */
        @SuppressWarnings("unchecked")
        public <C extends Channel, B extends AbstractBootstrap<B, C>> Class<? extends B> getBootstrapClass() {
            return (Class<? extends B>) bootstrapClass;
        }
    }
}
