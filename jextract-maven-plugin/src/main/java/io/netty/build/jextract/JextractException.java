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

/**
 * The single, framework-agnostic failure of the plugin's internals, binding validation, executable
 * resolution, the version check, and the generation run all raise it. Keeping it Maven-free lets those
 * collaborators stay free of the Maven plugin API; {@link GenerateMojo} owns the sole translation into
 * the {@code MojoExecutionException}/{@code MojoFailureException} taxonomy (see
 * {@code GenerateMojo.rethrowAsMojo}).
 *
 * <p>The {@link Category} decides that translation:
 *
 * <ul>
 *   <li>{@link Category#BUILD_FAILURE}, a problem in the user's input the user can act on: an invalid
 *       {@code <binding>}, a version mismatch, a non-zero jextract exit, or a timeout (the
 *       per-invocation limit elapsed before jextract finished, raise {@code timeoutSeconds} or fix a
 *       wedged header). Maps to {@code MojoFailureException}.</li>
 *   <li>{@link Category#EXECUTION_ERROR}, an environment problem where jextract never reached a
 *       verdict: it could not be located or started, the wait was interrupted, or its output could not
 *       be read. Maps to {@code MojoExecutionException}.</li>
 * </ul>
 *
 * <p>Construct it through the intent-named factories in {@link JextractExceptions}
 * ({@code buildFailure(...)}/{@code executionError(...)}) rather than a raw constructor, so each
 * throw site reads as the category it means.
 */
final class JextractException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Which Maven exception a {@link JextractException} translates to. See the class Javadoc for the
     * meaning of each value.
     */
    enum Category {
        BUILD_FAILURE,
        EXECUTION_ERROR
    }

    private final Category category;

    JextractException(final String message, final Category category) {
        super(message);
        this.category = category;
    }

    JextractException(final String message, final Throwable cause, final Category category) {
        super(message, cause);
        this.category = category;
    }

    Category category() {
        return category;
    }

    boolean isBuildFailure() {
        return category == Category.BUILD_FAILURE;
    }
}
