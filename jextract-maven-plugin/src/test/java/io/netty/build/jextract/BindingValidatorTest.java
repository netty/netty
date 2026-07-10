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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingValidatorTest {

    @Test
    void acceptsCompleteNonConflictingBindings() {
        final Binding one = binding("a.h", "Aaa");
        one.setStructs(singletonList("s1"));
        final Binding two = binding("b.h", "Bbb");
        two.setStructs(singletonList("s2"));

        assertDoesNotThrow(() -> BindingValidator.validate(Arrays.asList(one, two)));
    }

    @Test
    void rejectsBlankHeaderAndNamesTheBindingByClassName() {
        final JextractException e = assertThrows(JextractException.class,
                () -> BindingValidator.validate(singletonList(binding(null, "BsdSocket"))));
        assertTrue(e.isBuildFailure());
        assertEquals(JextractException.Category.BUILD_FAILURE, e.category());
        assertTrue(e.getMessage().contains("<header>"), e.getMessage());
        assertTrue(e.getMessage().contains("BsdSocket"), e.getMessage());
    }

    @Test
    void rejectsBlankClassNameAndNamesTheBindingByHeader() {
        final JextractException e = assertThrows(JextractException.class,
                () -> BindingValidator.validate(singletonList(binding("socket.h", "  "))));
        assertTrue(e.getMessage().contains("<className>"), e.getMessage());
        assertTrue(e.getMessage().contains("socket.h"), e.getMessage());
    }

    @Test
    void describesABindingMissingBothHeaderAndClassName() {
        // Neither field is set, so there is no name to report, the message must still be actionable.
        final JextractException e = assertThrows(JextractException.class,
                () -> BindingValidator.validate(singletonList(new Binding())));
        assertTrue(e.getMessage().contains("A <binding>"), e.getMessage());
        assertTrue(e.getMessage().contains("<header>"), e.getMessage());
    }

    @Test
    void rejectsDuplicateClassNameNamingBothHeaders() {
        final JextractException e = assertThrows(JextractException.class,
                () -> BindingValidator.validate(Arrays.asList(
                        binding("a.h", "Dup"), binding("b.h", "Dup"))));
        assertTrue(e.getMessage().contains("Duplicate <className>"), e.getMessage());
        assertTrue(e.getMessage().contains("Dup"), e.getMessage());
        assertTrue(e.getMessage().contains("a.h"), e.getMessage());
        assertTrue(e.getMessage().contains("b.h"), e.getMessage());
    }

    @Test
    void rejectsSharedStructNamingBothBindings() {
        final Binding one = binding("a.h", "Aaa");
        one.setStructs(singletonList("sockaddr_in"));
        final Binding two = binding("b.h", "Bbb");
        two.setStructs(singletonList("sockaddr_in"));

        final JextractException e = assertThrows(JextractException.class,
                () -> BindingValidator.validate(Arrays.asList(one, two)));
        assertTrue(e.getMessage().contains("sockaddr_in"), e.getMessage());
        assertTrue(e.getMessage().contains("clobber"), e.getMessage());
        assertTrue(e.getMessage().contains("Aaa"), e.getMessage());
        assertTrue(e.getMessage().contains("Bbb"), e.getMessage());
    }

    @Test
    void rejectsSharedTypedef() {
        final Binding one = binding("a.h", "Aaa");
        one.setTypedefs(singletonList("socklen_t"));
        final Binding two = binding("b.h", "Bbb");
        two.setTypedefs(singletonList("socklen_t"));

        final JextractException e = assertThrows(JextractException.class,
                () -> BindingValidator.validate(Arrays.asList(one, two)));
        assertTrue(e.getMessage().contains("socklen_t"), e.getMessage());
        assertTrue(e.getMessage().contains("typedef"), e.getMessage());
    }

    @Test
    void rejectsSharedUnion() {
        final Binding one = binding("a.h", "Aaa");
        one.setUnions(singletonList("sockaddr_storage_u"));
        final Binding two = binding("b.h", "Bbb");
        two.setUnions(singletonList("sockaddr_storage_u"));

        final JextractException e = assertThrows(JextractException.class,
                () -> BindingValidator.validate(Arrays.asList(one, two)));
        assertTrue(e.getMessage().contains("sockaddr_storage_u"), e.getMessage());
        assertTrue(e.getMessage().contains("union"), e.getMessage());
    }

    @Test
    void rejectsTheSameSymbolRequestedAsDifferentKinds() {
        // jextract names the standalone type file by the bare symbol, so a <struct> in one binding and
        // a <union> of the same name in another still collide on disk.
        final Binding one = binding("a.h", "Aaa");
        one.setStructs(singletonList("shared_t"));
        final Binding two = binding("b.h", "Bbb");
        two.setUnions(singletonList("shared_t"));

        final JextractException e = assertThrows(JextractException.class,
                () -> BindingValidator.validate(Arrays.asList(one, two)));
        assertTrue(e.getMessage().contains("shared_t"), e.getMessage());
    }

    @Test
    void allowsTheSameSymbolRepeatedWithinOneBinding() {
        // A single binding requesting the same struct twice writes one file, no cross-binding clobber.
        final Binding one = binding("a.h", "Aaa");
        one.setStructs(Arrays.asList("sockaddr_in", "sockaddr_in"));

        assertDoesNotThrow(() -> BindingValidator.validate(singletonList(one)));
    }

    @Test
    void rejectsBlankSymbolEntryUpFront() {
        // A blank <struct>/<function>/etc. must fail during validation, before any jextract runs,
        // and name the offending binding.
        final Binding one = binding("a.h", "Aaa");
        one.setStructs(Arrays.asList("   ", "real_t"));

        final JextractException e = assertThrows(JextractException.class,
                () -> BindingValidator.validate(singletonList(one)));
        assertTrue(e.isBuildFailure());
        assertTrue(e.getMessage().contains("Aaa"), e.getMessage());
        assertTrue(e.getMessage().contains("<struct>"), e.getMessage());
    }

    @Test
    void toleratesNullSymbolLists() {
        final Binding one = binding("a.h", "Aaa");
        one.setStructs(null);
        one.setTypedefs(null);
        one.setUnions(null);

        assertDoesNotThrow(() -> BindingValidator.validate(singletonList(one)));
    }

    @Test
    void acceptsValidTargetPackage() {
        assertDoesNotThrow(() ->
                BindingValidator.validateTargetPackage("io.netty.channel.unix.ffm.generated"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsBlankTargetPackage(final String blank) {
        assertThrows(JextractException.class, () -> BindingValidator.validateTargetPackage(blank));
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "io.netty./tmp", "../..", ".", "io..netty"})
    void rejectsTargetPackageThatWouldEscapeTheOutputTree(final String bad) {
        // These are interpolated into a path that is deleted and rewritten on promote, so a segment
        // that is not a plain Java identifier (a slash, a bare "..", an empty segment) must be refused.
        final JextractException e = assertThrows(JextractException.class,
                () -> BindingValidator.validateTargetPackage(bad));
        assertTrue(e.getMessage().contains("valid Java package name"), e.getMessage());
    }

    private static Binding binding(final String header, final String className) {
        final Binding binding = new Binding();
        binding.setHeader(header);
        binding.setClassName(className);
        return binding;
    }
}
