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

import static net.gleamynode.netty.channel.Channels.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelHandler;
import net.gleamynode.netty.channel.ChannelPipeline;
import net.gleamynode.netty.channel.ChannelPipelineFactory;
import net.gleamynode.netty.channel.SimpleChannelHandler;
import net.gleamynode.netty.logging.Logger;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.uses net.gleamynode.netty.channel.ChannelFactory
 */
public class Bootstrap {

    private static Logger logger = Logger.getLogger(Bootstrap.class);

    private volatile ChannelFactory factory;
    private volatile ChannelPipeline pipeline = pipeline();
    private volatile ChannelPipelineFactory pipelineFactory = pipelineFactory(pipeline);
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

    public ChannelPipeline getPipeline() {
        return pipeline;
    }

    public void setPipeline(ChannelPipeline pipeline) {
        if (pipeline == null) {
            throw new NullPointerException("pipeline");
        }
        pipeline = this.pipeline;
        pipelineFactory = pipelineFactory(pipeline);
    }

    public Map<String, ChannelHandler> getPipelineAsMap() {
        ChannelPipeline pipeline = this.pipeline;
        if (pipeline == null) {
            throw new IllegalStateException("pipelineFactory in use");
        }
        return pipeline.toMap();
    }

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

    public ChannelPipelineFactory getPipelineFactory() {
        return pipelineFactory;
    }

    public void setPipelineFactory(ChannelPipelineFactory pipelineFactory) {
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
