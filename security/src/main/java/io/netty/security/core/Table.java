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

import java.util.List;

/**
 * A table is a collection of rules sorted according to its priority number.
 */
public interface Table extends RuleLookup, Comparable<Table>, LockMechanism {

    /**
     * This number determines on which level a table will be placed
     */
    int priority();

    /**
     * Table name
     */
    String name();

    /**
     * {@link List} of {@link Rule} associated with this {@link Table}
     */
    List<Rule> rules();

    /**
     * Add a {@link Rule} into this {@link Table}
     *
     * @param rule {@link Rule} instance
     */
    void addRule(Rule rule);

    /**
     * Remove a {@link Rule} from this {@link Table}
     *
     * @param rule {@link Rule} instance
     */
    void removeRule(Rule rule);

    @Override
    int compareTo(Table table);
}
