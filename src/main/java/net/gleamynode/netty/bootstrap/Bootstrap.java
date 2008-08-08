/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.bootstrap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelEventHandlerAdapter;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelUtil;
import net.gleamynode.netty.pipeline.Pipe;
import net.gleamynode.netty.pipeline.PipeHandler;
import net.gleamynode.netty.pipeline.Pipeline;
import net.gleamynode.netty.pipeline.PipelineFactory;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class Bootstrap {

    private static Logger logger = Logger.getLogger(Bootstrap.class.getName());

    private volatile ChannelFactory factory;
    private volatile Pipeline<ChannelEvent> pipeline = ChannelUtil.newPipeline();
    private volatile PipelineFactory<ChannelEvent> pipelineFactory =
        ChannelUtil.newPipelineFactory(pipeline);
    private volatile Map<String, Object> options = new HashMap<String, Object>();

    public Bootstrap() {
        super();
    }

    public Bootstrap(ChannelFactory channelFactory) {
        setFactory(channelFactory);
    }

    public ChannelFactory getFactory() {
        ChannelFactory factory = this.factory;
        if (factory == null) {
            throw new IllegalStateException(
                    "factory is not set yet.");
        }
        return factory;
    }

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

    public Pipeline<ChannelEvent> getPipeline() {
        return pipeline;
    }

    public void setPipeline(Pipeline<ChannelEvent> pipeline) {
        if (pipeline == null) {
            throw new NullPointerException("pipeline");
        }
        this.pipeline = pipeline;
        pipelineFactory = ChannelUtil.newPipelineFactory(pipeline);
    }

    public Map<String, PipeHandler<ChannelEvent>> getPipelineAsMap() {
        Pipeline<ChannelEvent> pipeline = this.pipeline;
        if (pipeline == null) {
            throw new IllegalStateException("pipelineFactory in use");
        }

        Map<String, PipeHandler<ChannelEvent>> map =
            new LinkedHashMap<String, PipeHandler<ChannelEvent>>();
        for (Pipe<ChannelEvent> p: pipeline) {
            map.put(p.getName(), p.getHandler());
        }
        return map;
    }

    public void setPipelineAsMap(Map<String, PipeHandler<ChannelEvent>> pipelineMap) {
        if (pipelineMap == null) {
            throw new NullPointerException("pipelineMap");
        }

        if (!isOrderedMap(pipelineMap)) {
            throw new IllegalArgumentException(
                    "filters is not an ordered map. Please try " +
                    LinkedHashMap.class.getName() + ".");
        }

        Pipeline<ChannelEvent> pipeline = ChannelUtil.newPipeline();
        for(Map.Entry<String, PipeHandler<ChannelEvent>> e: pipelineMap.entrySet()) {
            pipeline.addLast(e.getKey(), e.getValue());
        }

        setPipeline(pipeline);
    }

    public PipelineFactory<ChannelEvent> getPipelineFactory() {
        return pipelineFactory;
    }

    public void setPipelineFactory(PipelineFactory<ChannelEvent> pipelineFactory) {
        if (pipelineFactory == null) {
            throw new NullPointerException("pipelineFactory");
        }
        pipeline = null;
        this.pipelineFactory = pipelineFactory;
    }

    public Map<String, Object> getOptions() {
        return new TreeMap<String, Object>(options);
    }

    public void setOptions(Map<String, Object> options) {
        if (options == null) {
            throw new NullPointerException("options");
        }
        this.options = new HashMap<String, Object>(options);
    }

    public Object getOption(String key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        return options.get(key);
    }

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

    private static boolean isOrderedMap(Map<String, PipeHandler<ChannelEvent>> map) {
        Class<Map<String, PipeHandler<ChannelEvent>>> mapType = getMapClass(map);
        if (LinkedHashMap.class.isAssignableFrom(mapType)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(mapType.getSimpleName() + " is an ordered map.");
            }
            return true;
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                    mapType.getName() + " is not a " +
                    LinkedHashMap.class.getSimpleName());
        }

        // Detect Jakarta Commons Collections OrderedMap implementations.
        Class<?> type = mapType;
        while (type != null) {
            for (Class<?> i: type.getInterfaces()) {
                if (i.getName().endsWith("OrderedMap")) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine(
                                mapType.getSimpleName() +
                                " is an ordered map (guessed from that it " +
                                " implements OrderedMap interface.)");
                    }
                    return true;
                }
            }
            type = type.getSuperclass();
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                    mapType.getName() +
                    " doesn't implement OrderedMap interface.");
        }

        // Last resort: try to create a new instance and test if it maintains
        // the insertion order.
        logger.fine(
                "Last resort; trying to create a new map instance with a " +
                "default constructor and test if insertion order is " +
                "maintained.");

        Map<String, PipeHandler<ChannelEvent>> newMap;
        try {
            newMap = mapType.newInstance();
        } catch (Exception e) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(
                        Level.FINE,
                        "Failed to create a new map instance of '" +
                        mapType.getName() +"'.", e);
            }
            return false;
        }

        Random rand = new Random();
        List<String> expectedNames = new ArrayList<String>();
        PipeHandler<ChannelEvent> dummyHandler = new ChannelEventHandlerAdapter();
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
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine(
                                "The specified map didn't pass the insertion " +
                                "order test after " + (i + 1) + " tries.");
                    }
                    return false;
                }
            }
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                    "The specified map passed the insertion order test.");
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, PipeHandler<ChannelEvent>>> getMapClass(
            Map<String, PipeHandler<ChannelEvent>> map) {
        return (Class<Map<String, PipeHandler<ChannelEvent>>>) map.getClass();
    }
}
