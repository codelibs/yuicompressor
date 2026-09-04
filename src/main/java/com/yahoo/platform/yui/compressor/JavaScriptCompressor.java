/*
 * YUI Compressor
 * http://developer.yahoo.com/yui/compressor/
 * Author: Julien Lecomte - http://www.julienlecomte.net/
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */
package com.yahoo.platform.yui.compressor;

import org.mozilla.javascript.*;
import org.mozilla.javascript.ast.*;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.*;

/**
 * Simplified JavaScript compressor using Rhino 1.8.0 API
 *
 * This is a minimal implementation to verify compatibility with Rhino 1.8.0.
 * Full functionality will be added incrementally.
 */
public class JavaScriptCompressor {

    // Static fields for variable name munging (used by ScriptOrFnScope)
    static final ArrayList ones;
    static final ArrayList twos;
    static final ArrayList threes;
    static final Set builtin = new HashSet();
    static final Map literals = new HashMap();
    static final Set reserved = new HashSet();

    static {
        // Initialize short variable names for munging
        // This list contains all the 3 characters or less built-in global
        // symbols available in a browser.
        builtin.add("NaN");
        builtin.add("top");

        ones = new ArrayList();
        for (char c = 'a'; c <= 'z'; c++)
            ones.add(Character.toString(c));
        for (char c = 'A'; c <= 'Z'; c++)
            ones.add(Character.toString(c));

        twos = new ArrayList();
        for (int i = 0; i < ones.size(); i++) {
            String one = (String) ones.get(i);
            for (char c = 'a'; c <= 'z'; c++)
                twos.add(one + Character.toString(c));
            for (char c = 'A'; c <= 'Z'; c++)
                twos.add(one + Character.toString(c));
            for (char c = '0'; c <= '9'; c++)
                twos.add(one + Character.toString(c));
        }

        threes = new ArrayList();
        for (int i = 0; i < twos.size(); i++) {
            String two = (String) twos.get(i);
            for (char c = 'a'; c <= 'z'; c++)
                threes.add(two + Character.toString(c));
            for (char c = 'A'; c <= 'Z'; c++)
                threes.add(two + Character.toString(c));
            for (char c = '0'; c <= '9'; c++)
                threes.add(two + Character.toString(c));
        }

        // Remove two-letter JavaScript reserved words and built-in globals
        twos.remove("as");
        twos.remove("is");
        twos.remove("do");
        twos.remove("if");
        twos.remove("in");
        // ES6+ two-letter keywords
        twos.remove("of");
        twos.removeAll(builtin);

        // Remove three-letter JavaScript reserved words and built-in globals
        threes.remove("for");
        threes.remove("int");
        threes.remove("new");
        threes.remove("try");
        threes.remove("use");
        threes.remove("var");
        // ES6+ three-letter keywords
        threes.remove("let");
        threes.remove("get");
        threes.remove("set");
        threes.removeAll(builtin);

        // Initialize reserved words set
        reserved.add("abstract");
        reserved.add("boolean");
        reserved.add("break");
        reserved.add("byte");
        reserved.add("case");
        reserved.add("catch");
        reserved.add("char");
        reserved.add("class");
        reserved.add("const");
        reserved.add("continue");
        reserved.add("debugger");
        reserved.add("default");
        reserved.add("delete");
        reserved.add("do");
        reserved.add("double");
        reserved.add("else");
        reserved.add("enum");
        reserved.add("export");
        reserved.add("extends");
        reserved.add("false");
        reserved.add("final");
        reserved.add("finally");
        reserved.add("float");
        reserved.add("for");
        reserved.add("function");
        reserved.add("goto");
        reserved.add("if");
        reserved.add("implements");
        reserved.add("import");
        reserved.add("in");
        reserved.add("instanceof");
        reserved.add("int");
        reserved.add("interface");
        reserved.add("long");
        reserved.add("native");
        reserved.add("new");
        reserved.add("null");
        reserved.add("package");
        reserved.add("private");
        reserved.add("protected");
        reserved.add("public");
        reserved.add("return");
        reserved.add("short");
        reserved.add("static");
        reserved.add("super");
        reserved.add("switch");
        reserved.add("synchronized");
        reserved.add("this");
        reserved.add("throw");
        reserved.add("throws");
        reserved.add("transient");
        reserved.add("true");
        reserved.add("try");
        reserved.add("typeof");
        reserved.add("var");
        reserved.add("void");
        reserved.add("volatile");
        reserved.add("while");
        reserved.add("with");
        // ES6+ reserved words
        reserved.add("let");
        reserved.add("await");
        reserved.add("yield");
        reserved.add("of");
        reserved.add("async");
        reserved.add("from");
        reserved.add("get");
        reserved.add("set");
        reserved.add("target");
        reserved.add("meta");
    }

    // Pattern for matching special comments that should be preserved
    private static final java.util.regex.Pattern SPECIAL_COMMENT_PATTERN =
        java.util.regex.Pattern.compile("/\\*(!|@cc_on|@if|@elif|@else|@end|@set|@_)([\\s\\S]*?)\\*/");

    private final CompilerEnvirons compilerEnv;
    private final ErrorReporter errorReporter;
    private final CommentPreserver commentPreserver;
    private AstRoot ast;
    private ScopeBuilder scopeBuilder;
    private ScriptOrFnScope globalScope;
    // Kept so MungedCodeGenerator can read the original operator text for
    // optional chaining (Rhino's QUESTION_DOT node type does not, on its own,
    // say which link of a chain actually carries "?.").
    private String sourceText;

    public JavaScriptCompressor(Reader in, ErrorReporter reporter)
            throws IOException, EvaluatorException {

        // Use default error reporter if none provided
        if (reporter == null) {
            reporter = new ErrorReporter() {
                public void warning(String message, String sourceName,
                                  int line, String lineSource, int lineOffset) {
                    // Silent by default
                }

                public void error(String message, String sourceName,
                                int line, String lineSource, int lineOffset) {
                    System.err.println("Error: " + message);
                }

                public EvaluatorException runtimeError(String message, String sourceName,
                                                      int line, String lineSource, int lineOffset) {
                    return new EvaluatorException(message);
                }
            };
        }

        this.errorReporter = reporter;
        this.commentPreserver = new CommentPreserver();

        // Read the source into a string buffer for comment scanning and parsing
        StringBuilder sourceCode = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            sourceCode.append(buffer, 0, read);
        }
        String source = sourceCode.toString();
        this.sourceText = source;

        // Scan for special comments before parsing
        scanForSpecialComments(source);

        // Setup compiler environment - DON'T record comments to avoid them in toSource()
        this.compilerEnv = new CompilerEnvirons();
        this.compilerEnv.setRecordingComments(false);
        this.compilerEnv.setRecordingLocalJsDocComments(false);
        // Use ES6 language version for better ES6+ syntax support
        this.compilerEnv.setLanguageVersion(Context.VERSION_ES6);
        this.compilerEnv.setGenerateDebugInfo(false);
        this.compilerEnv.setErrorReporter(reporter);

        // Parse the JavaScript
        Parser parser = new Parser(this.compilerEnv);
        try {
            this.ast = parser.parse(new java.io.StringReader(source), null, 1);

            // Build scope tree for variable tracking and munging
            this.scopeBuilder = new ScopeBuilder();
            this.globalScope = this.scopeBuilder.buildScopeTree(this.ast);

        } catch (Exception e) {
            throw new EvaluatorException("Error parsing JavaScript: " + e.getMessage());
        }
    }

    /**
     * Scan source code for special comments that should be preserved
     */
    private void scanForSpecialComments(String source) {
        java.util.regex.Matcher matcher = SPECIAL_COMMENT_PATTERN.matcher(source);

        while (matcher.find()) {
            String commentContent = matcher.group(1) + matcher.group(2);
            String fullComment = matcher.group(0);

            // Determine comment type
            CommentPreserver.CommentType type;
            if (commentContent.startsWith("!")) {
                type = CommentPreserver.CommentType.KEEP;
                commentPreserver.addPreservedComment(
                    new CommentPreserver.PreservedComment(matcher.start(), fullComment, type)
                );
            } else if (commentContent.startsWith("@")) {
                type = CommentPreserver.CommentType.CONDITIONAL;
                commentPreserver.addPreservedComment(
                    new CommentPreserver.PreservedComment(matcher.start(), fullComment, type)
                );
            }
        }
    }

    // 6-parameter version (for backward compatibility)
    public void compress(Writer out, int linebreakpos,
                        boolean munge, boolean verbose,
                        boolean preserveAllSemiColons, boolean disableOptimizations)
            throws IOException {
        compress(out, null, linebreakpos, munge, verbose,
                preserveAllSemiColons, disableOptimizations, false);
    }

    // 8-parameter version (main implementation)
    public void compress(Writer out, Writer mungemap, int linebreakpos,
                        boolean munge, boolean verbose,
                        boolean preserveAllSemiColons, boolean disableOptimizations,
                        boolean preserveUnknownHints)
            throws IOException {

        try {
            String compressed;

            if (this.ast != null) {
                // Perform variable munging if requested
                if (munge) {
                    this.globalScope.munge();
                }

                // Generate code with munged variable names. The generator emits only
                // the separators the syntax genuinely requires, so its output is
                // already minified; nothing here may run a regex over it, since that
                // cannot tell generated syntax apart from string/template/regex
                // literal contents.
                MungedCodeGenerator generator = new MungedCodeGenerator(this.scopeBuilder, munge, this.sourceText);
                compressed = generator.generate(this.ast);

                // Add line breaks if requested, before comments are inserted so a
                // preserved comment can never be split by one.
                if (linebreakpos > 0) {
                    compressed = addLineBreaks(compressed, generator.getSafeBreakOffsets(), linebreakpos);
                }

                // Insert preserved comments
                compressed = commentPreserver.insertComments(compressed);

                out.write(compressed);

                // Write munge map if requested
                if (munge && mungemap != null) {
                    StringBuffer mapping = new StringBuffer();
                    this.globalScope.getFullMapping(mapping, "");
                    mungemap.write(mapping.toString());
                }
            }

        } catch (Exception e) {
            throw new IOException("Error compressing JavaScript: " + e.getMessage(), e);
        }
    }

    /**
     * Breaks {@code code} into lines no longer than {@code linebreakpos} where
     * possible, inserting a newline only at one of {@code safeOffsets} - positions
     * the generator recorded as falling between statements, never inside a
     * string, template literal or regex literal. A statement longer than
     * {@code linebreakpos} with no safe offset inside it is left on one line
     * rather than cut at an arbitrary character position.
     *
     * @param code The generated JavaScript code
     * @param safeOffsets Offsets into {@code code}, ascending, where a line break is safe
     * @param linebreakpos Target maximum line length
     * @return Code with line breaks inserted at safe offsets
     */
    private String addLineBreaks(String code, java.util.List<Integer> safeOffsets, int linebreakpos) {
        if (linebreakpos <= 0 || code.length() <= linebreakpos
                || safeOffsets == null || safeOffsets.isEmpty()) {
            return code;
        }

        StringBuilder result = new StringBuilder(code.length() + code.length() / linebreakpos + 8);
        int lineStart = 0;
        int i = 0;
        int n = safeOffsets.size();

        while (i < n) {
            int chosen = -1;
            while (i < n) {
                int offset = safeOffsets.get(i);
                if (offset <= lineStart || offset >= code.length()) {
                    i++;
                    continue;
                }
                if (offset - lineStart > linebreakpos && chosen != -1) {
                    // This offset would make the line too long and we already
                    // have a shorter candidate; break there and reconsider this
                    // offset for the next line.
                    break;
                }
                chosen = offset;
                i++;
            }
            if (chosen == -1) {
                break;
            }
            result.append(code, lineStart, chosen);
            result.append('\n');
            lineStart = chosen;
        }

        result.append(code, lineStart, code.length());
        return result.toString();
    }
}
