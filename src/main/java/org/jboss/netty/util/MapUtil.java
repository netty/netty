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
package org.jboss.netty.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class MapUtil {
    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(MapUtil.class);

    @SuppressWarnings("unchecked")
    public static boolean isOrderedMap(Map<?, ?> map) {
        Class<?> mapType = map.getClass();
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

        Map newMap;
        try {
            newMap = (Map) mapType.newInstance();
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Failed to create a new map instance of '" +
                        mapType.getName() +"'.", e);
            }
            return false;
        }

        Random rand = new Random();
        List<String> expectedKeys = new ArrayList<String>();
        String dummyValue = "dummyValue";
        for (int i = 0; i < 10000; i ++) {
            String key;
            do {
                key = String.valueOf(rand.nextInt());
            } while (newMap.containsKey(key));

            newMap.put(key, dummyValue);
            expectedKeys.add(key);

            Iterator<String> it = expectedKeys.iterator();
            for (Object actualKey: newMap.keySet()) {
                if (!it.next().equals(actualKey)) {
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

    private MapUtil() {
        // Unused
    }
}
