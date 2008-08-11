/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.bootstrap;

import static org.jboss.netty.channel.Channels.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * Helper class which helps a user initialize a {@link Channel}.  This class
 * provides the common data structure for its subclasses which implements an
 * actual channel initialization from the common data structure.  Please refer
 * to {@link ClientBootstrap} and {@link ServerBootstrap} for client side and
 * server-side channel initialization respectively.
 *
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.uses org.jboss.netty.channel.ChannelFactory
 */
public class Bootstrap {

    private static InternalLogger logger = InternalLoggerFactory.getInstance(Bootstrap.class);

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
        if (this.factory != null) {
            throw new IllegalStateException(
                    "factory can't change once set.");
        }
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        this.factory = factory;
    }

    /**
     * Returns the default {@link ChannelPipeline} which is cloned when a new
     * {@link Channel} is created.  Bootstrap creates a new pipeline which has
     * the same entries with the returned pipeline for a new {@link Channel}.
     *
     * @return the default {@link ChannelPipeline}. {@code null} if
     *         {@link #setPipelineFactory(ChannelPipelineFactory)} was
     *         called last time.
     */
    public ChannelPipeline getPipeline() {
        return pipeline;
    }

    /**
     * Sets the default {@link ChannelPipeline} which is cloned when a new
     * {@link Channel} is created.  Bootstrap creates a new pipeline which has
     * the same entries with the specified pipeline for a new channel. Calling
     * this method also sets the {@code pipelineFactory} property to an
     * internal {@link ChannelPipelineFactory} implementation which returns
     * a copy of the specified pipeline.
     */
    public void setPipeline(ChannelPipeline pipeline) {
        if (pipeline == null) {
            throw new NullPointerException("pipeline");
        }
        pipeline = this.pipeline;
        pipelineFactory = pipelineFactory(pipeline);
    }

    /**
     * Convenience method for {@link #getPipeline()} which returns the default
     * pipeline of this bootstrap as an ordered map.
     *
     * @throws IllegalStateException
     *         if {@link #setPipelineFactory(ChannelPipelineFactory)} is in
     *         use to create a new pipeline
     */
    public Map<String, ChannelHandler> getPipelineAsMap() {
        ChannelPipeline pipeline = this.pipeline;
        if (pipeline == null) {
            throw new IllegalStateException("pipelineFactory in use");
        }
        return pipeline.toMap();
    }

    /**
     * Convenience method for {@link #setPipeline} which sets the default
     * pipeline of this bootstrap from an ordered map.
     *
     * @throws IllegalArgumentException
     *         if the specified map is not an ordered map
     */
    public void setPipelineAsMap(Map<String, ChannelHandler> pipelineMap) {
        if (pipelineMap == null) {
            throw new NullPointerException("pipelineMap");
        }

        if (!isOrderedMap(pipelineMap)) {
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
     * Returns the options which configures a new {@link Channel}.
     */
    public Map<String, Object> getOptions() {
        return new TreeMap<String, Object>(options);
    }

    /**
     * Sets the options which configures a new {@link Channel}.
     */
    public void setOptions(Map<String, Object> options) {
        if (options == null) {
            throw new NullPointerException("options");
        }
        this.options = new HashMap<String, Object>(options);
    }

    /**
     * Returns the value of the option with the specified key.
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
     * an option with the same key, it's replaced with the new value.  If the
     * specified value is {@code null}, an existing option with the specified
     * key is removed.
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

    private static boolean isOrderedMap(Map<String, ChannelHandler> map) {
        Class<Map<String, ChannelHandler>> mapType = getMapClass(map);
        if (LinkedHashMap.class.isAssignableFrom(mapType)) {
            if (logger.isDebugEnabled()) {
                logger.debug(mapType.getSimpleName() + " is an ordered map.");
            }
            return true;
        }

        if (logger.isDebugEnabled()) {
            logger.debug(
                    mapType.getName() + " is not a " +
                    LinkedHashMap.class.getSimpleName());
        }

        // Detect Apache Commons Collections OrderedMap implementations.
        Class<?> type = mapType;
        while (type != null) {
            for (Class<?> i: type.getInterfaces()) {
                if (i.getName().endsWith("OrderedMap")) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                mapType.getSimpleName() +
                                " is an ordered map (guessed from that it " +
                                " implements OrderedMap interface.)");
                    }
                    return true;
                }
            }
            type = type.getSuperclass();
        }

        if (logger.isDebugEnabled()) {
            logger.debug(
                    mapType.getName() +
                    " doesn't implement OrderedMap interface.");
        }

        // Last resort: try to create a new instance and test if it maintains
        // the insertion order.
        logger.debug(
                "Last resort; trying to create a new map instance with a " +
                "default constructor and test if insertion order is " +
                "maintained.");

        Map<String, ChannelHandler> newMap;
        try {
            newMap = mapType.newInstance();
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Failed to create a new map instance of '" +
                        mapType.getName() +"'.", e);
            }
            return false;
        }

        Random rand = new Random();
        List<String> expectedNames = new ArrayList<String>();
        ChannelHandler dummyHandler = new SimpleChannelHandler();
        for (int i = 0; i < 65536; i ++) {
            String filterName;
            do {
                filterName = String.valueOf(rand.nextInt());
            } while (newMap.containsKey(filterName));

            newMap.put(filterName, dummyHandler);
            expectedNames.add(filterName);

            Iterator<String> it = expectedNames.iterator();
            for (Object key: newMap.keySet()) {
                if (!it.next().equals(key)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                "The specified map didn't pass the insertion " +
                                "order test after " + (i + 1) + " tries.");
                    }
                    return false;
                }
            }
        }

        logger.debug("The specified map passed the insertion order test.");
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, ChannelHandler>> getMapClass(
            Map<String, ChannelHandler> map) {
        return (Class<Map<String, ChannelHandler>>) map.getClass();
    }
}
