/*
 * Copyright 2022 The Netty Project
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
package io.netty.security.core.standards;

import io.netty.security.core.Table;
import io.netty.security.core.Tables;
import io.netty.util.internal.ObjectUtil;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

public final class StandardTables implements Tables {
    private final Map<Integer, Table> TABLES = new ConcurrentSkipListMap<>();

    private StandardTables() {
        // Prevent outside initialization
    }

    /**
     * Create a new {@link StandardTables} instance
     *
     * @return New {@link StandardTables} instance
     */
    public static StandardTables create() {
        return new StandardTables();
    }

    @Override
    public synchronized void addTable(Table table) {
        ObjectUtil.checkNotNull(table, "Table");

        // If we already contain a table with that priority
        // then thrown exception.
        if (TABLES.containsKey(table.priority())) {
            throw new IllegalArgumentException("A table already exists with the priority: " + table.priority());
        }

        TABLES.put(table.priority(), table);
    }

    /**
     * Remove a {@link Table}
     *
     * @param priority Priority of table to remove
     * @return {@link Boolean#TRUE} if removal was successful else {@link Boolean#FALSE}
     */
    @Override
    public synchronized boolean removeTable(int priority) {
        return TABLES.remove(priority) != null;
    }

    /**
     * Returns {@link Table}s {@link Collection}
     */
    @Override
    public Collection<Table> tables() {
        return TABLES.values();
    }

    @Override
    public Map<Integer, Table> tablesMap() {
        return TABLES;
    }
}
