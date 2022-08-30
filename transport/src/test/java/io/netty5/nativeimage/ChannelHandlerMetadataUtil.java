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
package io.netty5.nativeimage;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.NativeImageHandlerMetadataTest;
import org.junit.jupiter.api.Assertions;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates native-image reflection metadata for subtypes of {@link io.netty5.channel.ChannelHandler}.
 * <p>
 * To use, create a JUnit test in the desired Netty module and invoke {@link #generateMetadata(String, String...)} with:
 * 1. The relative path to the native-image handler reflection metadata file in the Netty module.
 * This path is relative to the root of the target Netty module.
 * 2. A list of packages present in the target Netty module that may contain subtypes of the ChannelHandler.
 * <p>
 * See {@link NativeImageHandlerMetadataTest}
 */
public final class ChannelHandlerMetadataUtil {

    @SuppressWarnings("UnstableApiUsage")
    private static final Type HANDLER_METADATA_LIST_TYPE = new TypeToken<List<HandlerMetadata>>() {
    }.getType();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private ChannelHandlerMetadataUtil() {
    }

    public static void generateMetadata(String projectRelativeResourcePath, String... packageNames) {
        Set<Class<? extends ChannelHandler>> subtypes = findChannelHandlerSubclasses(packageNames);

        if (Arrays.asList(packageNames).contains("io.netty5.channel")) {
            // We want the metadata for the ChannelHandler itself too
            subtypes.add(ChannelHandler.class);
        }

        Set<HandlerMetadata> handlerMetadata = subtypes.stream()
                .map(type -> new HandlerMetadata(type.getName(), new Condition(type.getName()), true))
                .collect(Collectors.toSet());

        Path existingMetadataPath = new File(projectRelativeResourcePath).toPath().toAbsolutePath();
        if (!Files.exists(existingMetadataPath)) {
            if (handlerMetadata.size() == 0) {
                return;
            }

            System.err.println("Native Image reflection metadata is required for handlers in this project. " +
                    "This metadata was not found under " + existingMetadataPath);
            System.err.println("Please create this file with the following content: ");
            System.err.println(getMetadataJsonString(handlerMetadata));
            Assertions.fail();
        }

        String existingMetadataJson = "";
        try {
            existingMetadataJson = Files.readString(existingMetadataPath);
        } catch (IOException e) {
            Assertions.fail("Failed to open the native-image metadata file at: " + existingMetadataPath, e);
        }
        List<HandlerMetadata> existingMetadata = gson.fromJson(existingMetadataJson, HANDLER_METADATA_LIST_TYPE);

        Set<HandlerMetadata> newMetadata = new HashSet<>(handlerMetadata);
        newMetadata.removeAll(existingMetadata);

        Set<HandlerMetadata> removedMetadata = new HashSet<>(existingMetadata);
        removedMetadata.removeAll(handlerMetadata);

        if (!newMetadata.isEmpty() || !removedMetadata.isEmpty()) {
            System.err.println("In the native-image handler metadata file at " + existingMetadataPath);

            if (!newMetadata.isEmpty()) {
                System.err.println("The following new metadata must be added:\n");
                System.err.println(getMetadataJsonString(newMetadata));
                System.err.println();
            }
            if (!removedMetadata.isEmpty()) {
                System.err.println("The following metadata must be removed:\n");
                System.err.println(getMetadataJsonString(removedMetadata));
                System.err.println();
            }

            System.err.println("Expected metadata file contents:\n");
            System.err.println(getMetadataJsonString(handlerMetadata));
            Assertions.fail();
        }
    }

    private static Set<Class<? extends ChannelHandler>> findChannelHandlerSubclasses(String... packageNames) {
        Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                        .forPackages(packageNames)
                        .filterInputsBy(s -> !s.contains("Test")));

        Set<Class<? extends ChannelHandler>> classes = reflections.getSubTypesOf(ChannelHandler.class);
        classes = classes.stream()
                .filter(ChannelHandlerMetadataUtil::isNotTestClass)
                .filter(clazz -> Stream.of(packageNames).anyMatch(name -> clazz.getName().startsWith(name)))
                .collect(Collectors.toSet());
        return classes;
    }

    private static boolean isNotTestClass(Class<? extends ChannelHandler> clazz) {
        String[] parts = clazz.getName().split("\\.");
        if (parts.length > 0) {
            URL classFile = clazz.getResource(parts[parts.length - 1] + ".class");
            if (classFile != null) {
                return !classFile.toString().contains("/test-classes/");
            }
        }
        return true;
    }

    private static String getMetadataJsonString(Set<HandlerMetadata> metadata) {
        List<HandlerMetadata> metadataList = new ArrayList<>(metadata);
        metadataList.sort((h1, h2) -> Collator.getInstance().compare(h1.name, h2.name));
        return gson.toJson(metadataList, HANDLER_METADATA_LIST_TYPE);
    }

    private static class Condition {
        Condition(String typeReachable) {
            this.typeReachable = typeReachable;
        }

        String typeReachable;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Condition condition = (Condition) o;
            return Objects.equals(typeReachable, condition.typeReachable);
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeReachable);
        }
    }

    private static class HandlerMetadata {
        String name;

        Condition condition;

        boolean queryAllPublicMethods;

        HandlerMetadata(String name, Condition condition, boolean queryAllPublicMethods) {
            this.name = name;
            this.condition = condition;
            this.queryAllPublicMethods = queryAllPublicMethods;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            HandlerMetadata that = (HandlerMetadata) o;
            return queryAllPublicMethods == that.queryAllPublicMethods
                    && Objects.equals(name, that.name)
                    && Objects.equals(condition, that.condition);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
