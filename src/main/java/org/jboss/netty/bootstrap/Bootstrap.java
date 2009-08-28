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
package org.jboss.netty.bootstrap;

import static org.jboss.netty.channel.Channels.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.internal.MapUtil;

/**
 * A helper class which initializes a {@link Channel}.  This class provides
 * the common data structure for its subclasses which actually initialize
 * {@link Channel}s and their child {@link Channel}s using the common data
 * structure.  Please refer to {@link ClientBootstrap}, {@link ServerBootstrap},
 * and {@link ConnectionlessBootstrap} for client side, server-side, and
 * connectionless (e.g. UDP) channel initialization respectively.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.uses org.jboss.netty.channel.ChannelFactory
 */
public class Bootstrap implements ExternalResourceReleasable {

    private volatile ChannelFactory factory;
    private volatile ChannelPipeline pipeline = pipeline();
    private volatile ChannelPipelineFactory pipelineFactory = pipelineFactory(pipeline);
    private volatile Map<String, Object> options = new HashMap<String, Object>();

    /**
     * Creates a new instance with no {@link ChannelFactory} set.
     * {@link #setFactory(ChannelFactory)} must be called at once before any
     * I/O operation is requested.
     */
    protected Bootstrap() {
        super();
    }

    /**
     * Creates a new instance with the specified initial {@link ChannelFactory}.
     */
    protected Bootstrap(ChannelFactory channelFactory) {
        setFactory(channelFactory);
    }

    /**
     * Returns the {@link ChannelFactory} that will be used to perform an
     * I/O operation.
     *
     * @throws IllegalStateException
     *         if the factory is not set for this bootstrap yet.
     *         The factory can be set in the constructor or
     *         {@link #setFactory(ChannelFactory)}.
     */
    public ChannelFactory getFactory() {
        ChannelFactory factory = this.factory;
        if (factory == null) {
            throw new IllegalStateException(
                    "factory is not set yet.");
        }
        return factory;
    }

    /**
     * Sets the {@link ChannelFactory} that will be used to perform an I/O
     * operation.  This method can be called only once and can't be called at
     * all if the factory was specified in the constructor.
     *
     * @throws IllegalStateException
     *         if the factory is already set
     */
    public void setFactory(ChannelFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        if (this.factory != null) {
            throw new IllegalStateException(
                    "factory can't change once set.");
        }
        this.factory = factory;
    }

    /**
     * Returns the default {@link ChannelPipeline} which is cloned when a new
     * {@link Channel} is created.  {@link Bootstrap} creates a new pipeline
     * which has the same entries with the returned pipeline for a new
     * {@link Channel}.
     *
     * @return the default {@link ChannelPipeline}
     *
     * @throws IllegalStateException
     *         if {@link #setPipelineFactory(ChannelPipelineFactory)} was
     *         called by a user last time.
     */
    public ChannelPipeline getPipeline() {
        ChannelPipeline pipeline = this.pipeline;
        if (pipeline == null) {
            throw new IllegalStateException(
                    "getPipeline() cannot be called " +
                    "if setPipelineFactory() was called.");
        }
        return pipeline;
    }

    /**
     * Sets the default {@link ChannelPipeline} which is cloned when a new
     * {@link Channel} is created.  {@link Bootstrap} creates a new pipeline
     * which has the same entries with the specified pipeline for a new channel.
     * <p>
     * Calling this method also sets the {@code pipelineFactory} property to an
     * internal {@link ChannelPipelineFactory} implementation which returns
     * a shallow copy of the specified pipeline.
     * <p>
     * Please note that this method is a convenience method that works only
     * when <b>1)</b> you create only one channel from this bootstrap (e.g.
     * one-time client-side or connectionless channel) or <b>2)</b> the
     * {@link ChannelPipelineCoverage} of all handlers in the pipeline is
     * {@code "all"}.  You have to use
     * {@link #setPipelineFactory(ChannelPipelineFactory)} if <b>1)</b> your
     * pipeline contains a {@link ChannelHandler} whose
     * {@link ChannelPipelineCoverage} is {@code "one"} and <b>2)</b> one or
     * more channels are going to be created by this bootstrap (e.g. server-side
     * channels).
     */
    public void setPipeline(ChannelPipeline pipeline) {
        if (pipeline == null) {
            throw new NullPointerException("pipeline");
        }
        this.pipeline = pipeline;
        pipelineFactory = pipelineFactory(pipeline);
    }

    /**
     * Dependency injection friendly convenience method for
     * {@link #getPipeline()} which returns the default pipeline of this
     * bootstrap as an ordered map.
     *
     * @throws IllegalStateException
     *         if {@link #setPipelineFactory(ChannelPipelineFactory)} was
     *         called by a user last time.
     */
    public Map<String, ChannelHandler> getPipelineAsMap() {
        ChannelPipeline pipeline = this.pipeline;
        if (pipeline == null) {
            throw new IllegalStateException("pipelineFactory in use");
        }
        return pipeline.toMap();
    }

    /**
     * Dependency injection friendly convenience method for
     * {@link #setPipeline(ChannelPipeline)} which sets the default pipeline of
     * this bootstrap from an ordered map.
     *
     * @throws IllegalArgumentException
     *         if the specified map is not an ordered map
     */
    public void setPipelineAsMap(Map<String, ChannelHandler> pipelineMap) {
        if (pipelineMap == null) {
            throw new NullPointerException("pipelineMap");
        }

        if (!MapUtil.isOrderedMap(pipelineMap)) {
            throw new IllegalArgumentException(
                    "pipelineMap is not an ordered map. " +
                    "Please use " +
                    LinkedHashMap.class.getName() + ".");
        }

        ChannelPipeline pipeline = pipeline();
        for(Map.Entry<String, ChannelHandler> e: pipelineMap.entrySet()) {
            pipeline.addLast(e.getKey(), e.getValue());
        }

        setPipeline(pipeline);
    }

    /**
     * Returns the {@link ChannelPipelineFactory} which creates a new
     * {@link ChannelPipeline} for a new {@link Channel}.
     *
     * @see #getPipeline()
     */
    public ChannelPipelineFactory getPipelineFactory() {
        return pipelineFactory;
    }

    /**
     * Sets the {@link ChannelPipelineFactory} which creates a new
     * {@link ChannelPipeline} for a new {@link Channel}.  Calling this method
     * invalidates the current {@code pipeline} property of this bootstrap.
     * Subsequent {@link #getPipeline()} and {@link #getPipelineAsMap()} calls
     * will raise {@link IllegalStateException}.
     *
     * @see #setPipeline(ChannelPipeline)
     * @see #setPipelineAsMap(Map)
     */
    public void setPipelineFactory(ChannelPipelineFactory pipelineFactory) {
        if (pipelineFactory == null) {
            throw new NullPointerException("pipelineFactory");
        }
        pipeline = null;
        this.pipelineFactory = pipelineFactory;
    }

    /**
     * Returns the options which configures a new {@link Channel} and its
     * child {@link Channel}s.  The names of the child {@link Channel} options
     * are prepended with {@code "child."} (e.g. {@code "child.keepAlive"}).
     */
    public Map<String, Object> getOptions() {
        return new TreeMap<String, Object>(options);
    }

    /**
     * Sets the options which configures a new {@link Channel} and its child
     * {@link Channel}s.  To set the options of a child {@link Channel}, prepend
     * {@code "child."} to the option name (e.g. {@code "child.keepAlive"}).
     */
    public void setOptions(Map<String, Object> options) {
        if (options == null) {
            throw new NullPointerException("options");
        }
        this.options = new HashMap<String, Object>(options);
    }

    /**
     * Returns the value of the option with the specified key.  To retrieve
     * the option value of a child {@link Channel}, prepend {@code "child."}
     * to the option name (e.g. {@code "child.keepAlive"}).
     *
     * @param key  the option name
     *
     * @return the option value if the option is found.
     *         {@code null} otherwise.
     */
    public Object getOption(String key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        return options.get(key);
    }

    /**
     * Sets an option with the specified key and value.  If there's already
     * an option with the same key, it is replaced with the new value.  If the
     * specified value is {@code null}, an existing option with the specified
     * key is removed.  To set the option value of a child {@link Channel},
     * prepend {@code "child."} to the option name (e.g. {@code "child.keepAlive"}).
     *
     * @param key    the option name
     * @param value  the option value
     */
    public void setOption(String key, Object value) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (value == null) {
            options.remove(key);
        } else {
            options.put(key, value);
        }
    }

    /**
     * {@inheritDoc}  This method simply delegates the call to
     * {@link ChannelFactory#releaseExternalResources()}.
     */
    public void releaseExternalResources() {
        ChannelFactory factory = this.factory;
        if (factory != null) {
            factory.releaseExternalResources();
        }
    }
}
