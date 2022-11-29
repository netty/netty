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
package io.netty.contrib.security.core.standards;

import io.netty.security.core.Table;
import io.netty.security.core.standards.StandardTable;
import io.netty.security.core.standards.StandardTables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StandardTablesTest {

    @Test
    void create() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                StandardTables.create();
            }
        });

        assertNotNull(StandardTables.create());
    }

    @Test
    void addTable() {
        Table table = StandardTable.of(1, "SimpleTable");

        StandardTables standardTables = StandardTables.create();
        standardTables.addTable(table);

        assertEquals(1, standardTables.tablesMap().size());
    }

    @Test
    void removeTable() {
        Table table = StandardTable.of(1, "SimpleTable");

        StandardTables standardTables = StandardTables.create();
        standardTables.addTable(table);

        assertEquals(table, standardTables.tablesMap().get(table.priority()));
        standardTables.removeTable(table.priority());

        assertEquals(0, standardTables.tablesMap().size());
    }

    @Test
    void tables() {
        Table table = StandardTable.of(1, "SimpleTable");

        StandardTables standardTables = StandardTables.create();
        standardTables.addTable(table);

        assertEquals(table, standardTables.tablesMap().get(table.priority()));
    }
}
