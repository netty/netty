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
package io.netty.security.core;

import java.util.Collection;
import java.util.Map;

/**
 * {@link Tables} holds multiple {@link Table}s according
 * to their priority and provides {@link Table} addition, removal,
 * and retrieval.
 */
public interface Tables {

    /**
     * Add a new {@link Table}
     *
     * @param table {@link Table} to add
     * @throws NullPointerException     If {@link Table} parameter is {@code null}
     * @throws IllegalArgumentException If a {@link Table} already exists with the same priority
     */
    void addTable(Table table);

    /**
     * Remove a {@link Table}
     *
     * @param priority Priority of table to remove
     * @return {@link Boolean#TRUE} if removal was successful else {@link Boolean#FALSE}
     */
    boolean removeTable(int priority);

    /**
     * Returns {@link Table}s {@link Collection}
     */
    Collection<Table> tables();

    /**
     * Returns the {@link Table}s {@link Map}
     */
    Map<Integer, Table> tablesMap();
}
