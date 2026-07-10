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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * Validates the configured {@code <bindings>} as a whole, the rules that concern how bindings
 * interact <em>with each other</em>, kept out of {@link GenerateMojo} so they can be unit-tested
 * without a mojo and without the Maven exception taxonomy.
 *
 * <p>Bindings are identified in diagnostics by their {@code <className>} (the class each one
 * generates), never by list position. Duplicates are <em>reported</em>, not silently collapsed;
 * that is the whole point of the checks here.
 */
final class BindingValidator {

    private BindingValidator() {
    }

    /**
     * Rejects a {@code targetPackage} that is not a valid Java package name. This is not only hygiene:
     * the package name is turned into a filesystem path that is deleted and rewritten when the freshly
     * generated sources are promoted, so a value containing a path segment (for example {@code ..} or
     * one with a slash) must never reach that code.
     *
     * @throws JextractException a build failure if {@code targetPackage} is blank or is not a dotted
     *                           run of Java identifiers
     */
    static void validateTargetPackage(@Nullable final String targetPackage) throws JextractException {
        if (StringUtils.isBlank(targetPackage) || !isQualifiedName(targetPackage.trim())) {
            throw JextractExceptions.buildFailure(
                    "<targetPackage> must be a valid Java package name; got '" + targetPackage + "'.");
        }
    }

    /** True if {@code name} is a non-empty dot-separated run of Java identifiers (no empty segments). */
    private static boolean isQualifiedName(final String name) {
        boolean startOfSegment = true;
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (c == '.') {
                if (startOfSegment) {
                    return false; // leading, trailing, or doubled dot leaves an empty segment
                }
                startOfSegment = true;
            } else if (startOfSegment) {
                if (!Character.isJavaIdentifierStart(c)) {
                    return false;
                }
                startOfSegment = false;
            } else if (!Character.isJavaIdentifierPart(c)) {
                return false;
            }
        }
        return !startOfSegment; // must not end on a dot (and rejects the empty string)
    }

    /**
     * Rejects incomplete or conflicting {@code <binding>} entries. Maven does not enforce
     * {@code required = true} on fields of nested config objects, so a {@code <binding>} missing
     * {@code <header>} or {@code <className>} reaches us and must be rejected here with an actionable
     * message. We additionally reject two kinds of silent-clobber:
     *
     * <ul>
     *   <li>duplicate {@code <className>}, two bindings would write the same header-class file;</li>
     *   <li>the same {@code <struct>}/{@code <typedef>}/{@code <union>} requested by more than one
     *       binding, jextract emits a <em>standalone</em> type file named after the C symbol (e.g.
     *       {@code sockaddr_in.java}), not after the header class, so two bindings sharing a type
     *       into the same {@code targetPackage} would overwrite each other's copy of that file, and
     *       the survivor back-references whichever binding ran last.</li>
     * </ul>
     *
     * @param bindings the configured bindings, in declaration order
     * @throws JextractException a {@link JextractException.Category#BUILD_FAILURE} if any binding is
     *                           incomplete or would clobber another
     */
    static void validate(final List<Binding> bindings) throws JextractException {
        // Keyed by the (unique) className, the binding's identity in diagnostics. classNameToHeader
        // maps it to its header so a duplicate can name both offending headers; typeSymbolToClassName
        // maps a standalone-type symbol to the binding that first claimed it.
        final Map<String, String> classNameToHeader = new HashMap<>();
        final Map<String, String> typeSymbolToClassName = new HashMap<>();
        for (final Binding binding : bindings) {
            final String header = binding.header();
            final String className = binding.className();
            if (StringUtils.isBlank(header)) {
                throw JextractExceptions.buildFailure(
                        describe(className) + " is missing a non-empty <header>.");
            }
            if (StringUtils.isBlank(className)) {
                throw JextractExceptions.buildFailure(
                        "The <binding> for header '" + header + "' is missing a non-empty <className>.");
            }
            // Two bindings that share a className write to the same generated file, silently
            // clobbering each other. Reject that up front, naming both headers.
            final String previousHeader = classNameToHeader.putIfAbsent(className, header);
            if (previousHeader != null) {
                throw JextractExceptions.buildFailure(
                        "Duplicate <className> '" + className + "' requested by headers '"
                                + previousHeader + "' and '" + header
                                + "'; each binding must generate a distinct class.");
            }
            rejectBlankSymbols(className, "function", binding.functions());
            rejectBlankSymbols(className, "struct", binding.structs());
            rejectBlankSymbols(className, "constant", binding.constants());
            rejectBlankSymbols(className, "typedef", binding.typedefs());
            rejectBlankSymbols(className, "union", binding.unions());
            rejectBlankSymbols(className, "var", binding.vars());
            rejectSharedTypeSymbols(typeSymbolToClassName, "struct", binding.structs(), className);
            rejectSharedTypeSymbols(typeSymbolToClassName, "typedef", binding.typedefs(), className);
            rejectSharedTypeSymbols(typeSymbolToClassName, "union", binding.unions(), className);
        }
    }

    /**
     * Records each standalone-type symbol against the binding ({@code className}) that requested it and
     * fails if another binding already claimed it. jextract writes these type files under the shared
     * {@code targetPackage} keyed only by the C symbol name (the kind, struct/typedef/union, does not
     * appear in the file name), so a clash on the bare name is what actually clobbers on disk.
     *
     * <p>Deliberately conservative for typedefs: a <em>primitive</em> typedef (e.g.
     * {@code typedef int prim_t}) is inlined by jextract and emits no standalone {@code prim_t.java},
     * so two bindings sharing one would not actually collide. We reject it anyway, the plugin cannot
     * know a symbol's kind without parsing the header, and a false rejection (refusing a config that
     * would have worked) is far safer than a silent overwrite.
     */
    private static void rejectSharedTypeSymbols(final Map<String, String> seen, final String kind,
                                                @Nullable final List<String> symbols, final String className)
            throws JextractException {
        if (symbols == null) {
            return;
        }
        for (final String symbol : symbols) {
            if (StringUtils.isBlank(symbol)) {
                continue; // JextractCommand rejects blanks; skip here so the message stays focused.
            }
            final String previousClassName = seen.putIfAbsent(symbol, className);
            // A binding may list the same symbol twice (it writes one file, so there is no clobber);
            // only a symbol already claimed by a *different* binding (className) is a clash.
            if (previousClassName != null && !previousClassName.equals(className)) {
                throw JextractExceptions.buildFailure(
                        "Type '" + symbol + "' (<" + kind + ">) is requested by bindings '"
                                + previousClassName + "' and '" + className + "'; jextract writes a single "
                                + symbol + ".java into the shared package, so they would clobber each "
                                + "other. Request each struct/typedef/union from at most one binding.");
            }
        }
    }

    /**
     * Rejects any blank symbol entry up front (an empty {@code <function>}, {@code <struct>}, and so
     * on), so a mistake in the last binding fails before jextract runs on the first, rather than
     * surfacing only when {@link JextractCommand} assembles that binding mid-loop.
     */
    private static void rejectBlankSymbols(final String className, final String kind,
                                           @Nullable final List<String> symbols) throws JextractException {
        if (symbols == null) {
            return;
        }
        for (final String symbol : symbols) {
            if (StringUtils.isBlank(symbol)) {
                throw JextractExceptions.buildFailure("Binding '" + className + "' has an empty <" + kind
                        + "> entry; remove it or give it a symbol name.");
            }
        }
    }

    /** Names a binding for a diagnostic, tolerating the (unusual) case where even its className is unset. */
    private static String describe(@Nullable final String className) {
        return StringUtils.isBlank(className) ? "A <binding>" : "The <binding> '" + className + "'";
    }
}
