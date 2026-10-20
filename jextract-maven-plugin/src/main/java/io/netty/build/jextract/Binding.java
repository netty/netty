/*
 * Copyright 2026 The Netty Project
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
package io.netty.build.jextract;

import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Declares a single jextract invocation: one C header turned into one generated Java class.
 *
 * <p>Each list of symbols maps to the matching jextract {@code --include-*} flag; jextract only
 * emits the symbols that are explicitly requested.
 */
public final class Binding {

    /**
     * Header passed to jextract verbatim as the header to parse; libclang resolves it against the
     * active system SDK (e.g. {@code sys/socket.h}).
     */
    @Nullable
    @Parameter(required = true)
    private String header;

    /**
     * Name of the generated Java class, passed to jextract as {@code --header-class-name}
     * (e.g. {@code BsdSocket}).
     */
    @Nullable
    @Parameter(required = true)
    private String className;

    /**
     * Functions to include ({@code --include-function}).
     */
    @Parameter
    private List<String> functions = new ArrayList<>();

    /**
     * Structs to include ({@code --include-struct}).
     */
    @Parameter
    private List<String> structs = new ArrayList<>();

    /**
     * Constants to include ({@code --include-constant}).
     */
    @Parameter
    private List<String> constants = new ArrayList<>();

    /**
     * Typedefs to include ({@code --include-typedef}), e.g. {@code socklen_t}.
     */
    @Parameter
    private List<String> typedefs = new ArrayList<>();

    /**
     * Unions to include ({@code --include-union}).
     */
    @Parameter
    private List<String> unions = new ArrayList<>();

    /**
     * Global variables to include ({@code --include-var}), e.g. {@code errno}-style globals.
     */
    @Parameter
    private List<String> vars = new ArrayList<>();

    /**
     * Creates an empty binding. Must stay {@code public} so Maven's configurator can instantiate it
     * reflectively; Maven then populates the fields via field injection. Tests also use it directly.
     */
    public Binding() {
    }

    @Nullable
    String header() {
        return header;
    }

    @Nullable
    String className() {
        return className;
    }

    List<String> functions() {
        return functions;
    }

    List<String> structs() {
        return structs;
    }

    List<String> constants() {
        return constants;
    }

    List<String> typedefs() {
        return typedefs;
    }

    List<String> unions() {
        return unions;
    }

    List<String> vars() {
        return vars;
    }

    // Setters, used by tests to build instances (Maven itself uses field injection).

    void setHeader(final String header) {
        this.header = header;
    }

    void setClassName(final String className) {
        this.className = className;
    }

    void setFunctions(final List<String> functions) {
        this.functions = functions;
    }

    void setStructs(final List<String> structs) {
        this.structs = structs;
    }

    void setConstants(final List<String> constants) {
        this.constants = constants;
    }

    void setTypedefs(final List<String> typedefs) {
        this.typedefs = typedefs;
    }

    void setUnions(final List<String> unions) {
        this.unions = unions;
    }

    void setVars(final List<String> vars) {
        this.vars = vars;
    }

    @Override
    public String toString() {
        return "Binding{header=" + header + ", className=" + className + '}';
    }
}
