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
import java.util.Locale;
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
     * message.
     *
     * <p>Every binding generates files into one shared {@code targetPackage} directory: a
     * {@code <className>.java} for the header class, plus a <em>standalone</em> {@code <symbol>.java}
     * for each requested {@code <struct>}/{@code <typedef>}/{@code <union>} (jextract names the type
     * file after the bare C symbol, not the header class). They all share a single on-disk namespace,
     * so we reject any pair that would resolve to the same file:
     *
     * <ul>
     *   <li>two bindings with the same {@code <className>};</li>
     *   <li>the same struct/typedef/union requested by two different bindings;</li>
     *   <li>a struct/typedef/union whose name equals a binding's {@code <className>}, the type file
     *       would clobber the header-class file, even within the same binding.</li>
     * </ul>
     *
     * <p>Names are compared <em>case-insensitively</em>: the files land on the generating machine's
     * filesystem (macOS and Windows are case-insensitive), where {@code Socket.java} and
     * {@code socket.java} are the same file. Comparison uses the trimmed name, matching what
     * {@link JextractCommand} passes to jextract.
     *
     * @param bindings the configured bindings, in declaration order
     * @throws JextractException a {@link JextractException.Category#BUILD_FAILURE} if any binding is
     *                           incomplete or would clobber another
     */
    static void validate(final List<Binding> bindings) throws JextractException {
        // A single map from normalized (trimmed, lower-cased) file name to the thing that claims it, so
        // a collision between any two generated files is caught regardless of kind. Header classes are
        // registered first, so a later type symbol matching a class always reports the class as the
        // prior claim; the standalone type symbols are registered in the second pass.
        final Map<String, Claim> byFileName = new HashMap<>();
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
            final Claim incoming = Claim.headerClass(className.trim(), header);
            final Claim previous = byFileName.putIfAbsent(key(className), incoming);
            if (previous != null) {
                // Only header classes are registered in this pass, so previous is another binding's class.
                throw JextractExceptions.buildFailure(
                        "Duplicate <className> '" + incoming.display + "' requested by headers '"
                                + previous.header + "' and '" + header + "'; each binding must generate a "
                                + "distinct class (names are compared case-insensitively).");
            }
        }
        for (final Binding binding : bindings) {
            final String className = binding.className().trim();
            rejectBlankSymbols(className, "function", binding.functions());
            rejectBlankSymbols(className, "struct", binding.structs());
            rejectBlankSymbols(className, "constant", binding.constants());
            rejectBlankSymbols(className, "typedef", binding.typedefs());
            rejectBlankSymbols(className, "union", binding.unions());
            rejectBlankSymbols(className, "var", binding.vars());
            registerTypeSymbols(byFileName, "struct", binding.structs(), className);
            registerTypeSymbols(byFileName, "typedef", binding.typedefs(), className);
            registerTypeSymbols(byFileName, "union", binding.unions(), className);
        }
    }

    /**
     * Registers each standalone-type symbol against the file it would generate and fails on any clash.
     * A symbol already claimed by the <em>same</em> binding is tolerated (it writes one file, so there
     * is no clobber); a clash with a different binding's type symbol, or with any header class (even the
     * declaring binding's own), is a silent overwrite and is rejected.
     *
     * <p>Deliberately conservative for typedefs: a <em>primitive</em> typedef (e.g.
     * {@code typedef int prim_t}) is inlined by jextract and emits no standalone {@code prim_t.java}, so
     * it would not actually collide. We reject it anyway, the plugin cannot know a symbol's kind without
     * parsing the header, and a false rejection is far safer than a silent overwrite.
     */
    private static void registerTypeSymbols(final Map<String, Claim> byFileName, final String kind,
                                            @Nullable final List<String> symbols, final String owner)
            throws JextractException {
        if (symbols == null) {
            return;
        }
        for (final String symbol : symbols) {
            if (StringUtils.isBlank(symbol)) {
                continue; // rejectBlankSymbols already failed on this; skip so the message stays focused.
            }
            final Claim incoming = Claim.typeSymbol(symbol.trim(), owner);
            final Claim existing = byFileName.get(key(symbol));
            if (existing == null) {
                byFileName.put(key(symbol), incoming);
                continue;
            }
            if (existing.headerClass) {
                throw JextractExceptions.buildFailure(
                        "The <" + kind + "> '" + incoming.display + "' requested by binding '" + owner
                                + "' collides with the header class of binding '" + existing.owner
                                + "'; jextract writes both as " + existing.display + ".java into the shared "
                                + "package (file names are compared case-insensitively), so they would "
                                + "clobber each other. Change the <className> of binding '" + existing.owner
                                + "' or the conflicting <" + kind + "> name in binding '" + owner
                                + "' so they no longer map to the same generated file.");
            }
            if (!existing.owner.equals(owner)) {
                throw JextractExceptions.buildFailure(
                        "Type '" + incoming.display + "' (<" + kind + ">) is requested by bindings '"
                                + existing.owner + "' and '" + owner + "'; jextract writes a single "
                                + incoming.display + ".java into the shared package, so they would clobber "
                                + "each other. Request each struct/typedef/union from at most one binding.");
            }
            // Same binding: tolerate only an exact repeat (one file). A name differing only in case is
            // a second, distinct symbol whose file collides on a case-insensitive filesystem.
            if (!existing.display.equals(incoming.display)) {
                throw JextractExceptions.buildFailure(
                        "Binding '" + owner + "' requests '" + existing.display + "' and '"
                                + incoming.display + "' (<" + kind + ">), which differ only in case; "
                                + "jextract writes both as " + incoming.display + ".java into the shared "
                                + "package (file names are compared case-insensitively), so they would "
                                + "clobber each other. Drop or change one of these <" + kind + "> entries "
                                + "so they no longer map to the same generated file.");
            }
            // Exact repeat, possibly under another kind; one file, no clobber.
        }
    }

    /** The shared file-name key: trimmed and lower-cased, matching the case-insensitive target FS. */
    private static String key(final String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * One claim on a generated file name: either a binding's header class ({@code <className>.java}) or
     * a standalone type file ({@code <symbol>.java}). {@link #display} keeps the original case for
     * diagnostics; {@link #owner} is the {@code <className>} of the declaring binding.
     */
    private static final class Claim {
        private final boolean headerClass;
        private final String display;
        private final String owner;
        @Nullable
        private final String header;

        private Claim(final boolean headerClass, final String display, final String owner,
                      @Nullable final String header) {
            this.headerClass = headerClass;
            this.display = display;
            this.owner = owner;
            this.header = header;
        }

        static Claim headerClass(final String className, final String header) {
            return new Claim(true, className, className, header);
        }

        static Claim typeSymbol(final String symbol, final String owner) {
            return new Claim(false, symbol, owner, null);
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
