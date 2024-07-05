/*
 * Copyright 2024 The Netty Project
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

package io.netty.testsuite_jpms.test;

import io.netty.bootstrap.ChannelInitializerExtension;
import org.junit.jupiter.api.Test;

import java.lang.module.ModuleDescriptor;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class CheckModuleDescriptorTest {

    private static final Set<String> AUTOMATIC_MODULES_ALLOWED = Set.of(
            "com.google.protobuf", "protobuf.javanano", "jboss.marshalling", "jboss.marshalling.serial");

    /**
     * Ensure that classpath is empty and all module are named and not automatic.
     */
    @Test
    public void checkExplicitModules() {
        String classpath = System.getProperty("java.class.path");
        assertEquals("", classpath);
        ModuleLayer layer = ModuleLayer.boot();
        layer.modules().forEach(module -> {
            assertTrue(module.isNamed(), "Module " + module.getName() + " is not named");
            boolean automatic = AUTOMATIC_MODULES_ALLOWED.contains(module.getName());
            if (!automatic) {
                assertFalse(module.getDescriptor().isAutomatic(), "Unexpected automatic module "
                        + module.getName());
            }
        });
    }

    @Test
    public void testTransportChannelInitializerExtensionUseDeclaration() {
        Optional<Module> opt = ModuleLayer.boot().findModule("io.netty.transport");
        assertTrue(opt.isPresent());
        Module module = opt.get();
        Set<String> providesSet = module.getDescriptor().uses();
        assertEquals(1, providesSet.size());
        String use = providesSet.iterator().next();
        assertEquals(ChannelInitializerExtension.class.getName(), use);
    }

}
