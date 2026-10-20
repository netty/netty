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

import java.io.IOException;
import java.util.List;

/**
 * Starts a native process for a fully-resolved argument vector. The seam that lets tests substitute
 * a fake {@link Process} for the real jextract binary.
 *
 * <p>Shared by the two collaborators that launch jextract, {@link JextractCommand} (generation)
 * and {@link JextractVersionChecker} (the {@code --version} check), so it is a package-level type
 * rather than nested in either. {@link ProcessStarters} supplies the production implementations.
 */
@FunctionalInterface
interface ProcessStarter {
    Process start(List<String> command) throws IOException;
}
