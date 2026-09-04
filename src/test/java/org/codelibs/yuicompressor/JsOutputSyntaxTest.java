package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * Feeds the compressed output of every JS fixture to "node --check". An expected
 * value comparison only protects cases somebody thought of; this catches any
 * output that is not valid JavaScript at all.
 */
class JsOutputSyntaxTest {

    private static final Path RESOURCES = Paths.get("src/test/resources");

    private static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String m, String s, int l, String ls, int lo) {
        }

        public void error(String m, String s, int l, String ls, int lo) {
        }

        public EvaluatorException runtimeError(String m, String s, int l, String ls, int lo) {
            return new EvaluatorException(m);
        }
    };

    static boolean nodeAvailable() {
        try {
            return new ProcessBuilder("node", "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static Stream<String> fixtures() throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".js"))
                    .filter(n -> !n.startsWith("_"))
                    .sorted()
                    .collect(Collectors.toList())
                    .stream();
        }
    }

    @EnabledIf("nodeAvailable")
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void compressedOutputParses(String fixture) throws Exception {
        String source = new String(Files.readAllBytes(RESOURCES.resolve(fixture)), StandardCharsets.UTF_8);

        // Some fixtures are not valid JavaScript to begin with. Rhino is lenient
        // enough to accept them, but the compressor cannot be asked to turn
        // invalid input into valid output, so they are not this test's business.
        // promise-catch-finally-issue203.js is one: it contains
        // "new Promise(resolve, reject) {}", which node rejects.
        if (nodeRejects(source)) {
            return;
        }

        JavaScriptCompressor compressor;
        try {
            compressor = new JavaScriptCompressor(new StringReader(source), SILENT);
        } catch (EvaluatorException parseFailure) {
            // Input the compressor cannot parse is a separate concern; this test only
            // asserts that whatever it does emit is valid JavaScript. A failure during
            // code generation is NOT excused here — it must fail the test.
            return;
        }

        StringWriter out = new StringWriter();
        compressor.compress(out, -1, true, false, false, false);

        String report = nodeCheck(out.toString());
        assertEquals("", report, "node rejected the compressed output of " + fixture);
    }

    // "node --check" only proves the output PARSES, never that it MEANS the
    // same thing. Node's non-strict scripts still support the legacy Annex
    // B.1.3 "HTML-like comments" grammar, under which "<!--" anywhere, or
    // "-->" at the start of a line, silently opens a single-line comment
    // instead of raising a syntax error - so does a plain "//" or "/*" that
    // the generator never intended as a comment (e.g. a division operator
    // immediately followed by a regex literal, with no separating space).
    // Two separate Criticals in this release were exactly that: comment
    // injection that node accepted as valid. The class of dangerous
    // sequences is closed at four members, so it is closed for good here.
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void compressedOutputHasNoCommentInjection(String fixture) throws Exception {
        String source = new String(Files.readAllBytes(RESOURCES.resolve(fixture)), StandardCharsets.UTF_8);

        JavaScriptCompressor compressor;
        try {
            compressor = new JavaScriptCompressor(new StringReader(source), SILENT);
        } catch (EvaluatorException parseFailure) {
            // Same rationale as compressedOutputParses: input the compressor
            // cannot parse is not this test's business.
            return;
        }

        StringWriter out = new StringWriter();
        compressor.compress(out, -1, true, false, false, false);

        List<String> violations = new CommentInjectionScanner(out.toString()).scan();
        assertEquals(List.of(), violations, "compressed output of " + fixture + " contains a sequence "
                + "node would silently read as a comment");
    }

    // Direct tests of the scanner itself: it must actually catch each of the
    // four dangerous sequences, and it must not flag legitimate look-alikes
    // (the same shapes appearing inside strings, regexes, or a preserved
    // comment) - jquery-1.6.4.js's own compressed output contains genuine
    // "//" both in string literals and inside regex literals, so a scanner
    // that flagged those would be useless in practice.

    @Test
    public void scannerCatchesHtmlCommentOpen() {
        assertEquals(1, new CommentInjectionScanner("a<!--b").scan().size());
    }

    @Test
    public void scannerCatchesHtmlCommentCloseAtStartOfLine() {
        assertEquals(1, new CommentInjectionScanner("a;\n-->b").scan().size());
    }

    @Test
    public void scannerAllowsHtmlCommentCloseNotAtStartOfLine() {
        // "a-->b" is valid JS: "a-- > b". Only a line-initial "-->" is the
        // Annex B.1.3 comment opener.
        assertEquals(List.of(), new CommentInjectionScanner("a-->b").scan());
    }

    @Test
    public void scannerCatchesDivisionBeforeRegexMergingIntoLineComment() {
        // What a missing separator between a division and a following regex
        // literal produces: "a/ /re/.test(x)" minified to "a//re/.test(x)".
        assertEquals(1, new CommentInjectionScanner("a//re/.test(x)").scan().size());
    }

    @Test
    public void scannerCatchesBareBlockComment() {
        assertEquals(1, new CommentInjectionScanner("a;/* not preserved */b").scan().size());
    }

    @Test
    public void scannerAllowsPreservedBangComment() {
        assertEquals(List.of(), new CommentInjectionScanner("/*! license text */a=1").scan());
    }

    @Test
    public void scannerAllowsSlashSlashInsideRegexLiteral() {
        // From jquery-1.6.4.js's own compressed output: c=/^\/\//
        assertEquals(List.of(), new CommentInjectionScanner("c=/^\\/\\//").scan());
    }

    @Test
    public void scannerAllowsSlashSlashInsideStringLiteral() {
        assertEquals(List.of(), new CommentInjectionScanner("a=\"http://x\"").scan());
    }

    @Test
    public void scannerAllowsRegexAfterKeyword() {
        assertEquals(List.of(), new CommentInjectionScanner("function f(){return/x/.test(a)}").scan());
    }

    @Test
    public void scannerScansLiveCodeInsideTemplateSubstitution() {
        assertEquals(1, new CommentInjectionScanner("`a${1<!--2}b`").scan().size());
    }

    @Test
    public void scannerAllowsCommentLikeTextInsideTemplateLiteral() {
        assertEquals(List.of(), new CommentInjectionScanner("`<!--not live-->`").scan());
    }

    /**
     * Scans compressed JavaScript for the four comment-injection sequences,
     * everywhere they would actually be read as live code. Occurrences inside
     * string, template, and regex literals are inert text and are skipped, as
     * is the interior of a preserved "/*!" banner comment (already a real
     * comment, so embedded text there is harmless). Distinguishing a regex
     * literal from a division operator - the classic JS lexing ambiguity -
     * uses the standard heuristic of looking at the preceding significant
     * token; this is a pragmatic guard against a specific, narrow bug class,
     * not a general-purpose lexer, so unusual token sequences (a contextual
     * keyword used as a property name, for instance) are not guaranteed to be
     * classified perfectly, though a misclassification here can only cause a
     * missed regex boundary, not a missed injection: two real "/" characters
     * end up adjacent only when they actually are, regardless of who owns
     * them.
     */
    private static final class CommentInjectionScanner {

        private static final Set<String> REGEX_PRECURSORS = Set.of("(", ",", "=", ":", "[", "!", "&", "|", "?", "{",
                "}", ";", "+", "-", "*", "%", "<", ">", "^", "~", "return", "typeof", "instanceof", "in", "of", "new",
                "delete", "void", "throw", "yield", "case", "do", "else");

        private final String code;
        private final List<String> violations = new ArrayList<>();
        private int pos;
        private boolean atLineStart = true;
        private String lastToken = "";

        CommentInjectionScanner(String code) {
            this.code = code;
        }

        List<String> scan() {
            scanLiveCode(false);
            return violations;
        }

        /**
         * Scans live code from {@link #pos} onward. When {@code insideTemplateExpr}
         * is true, returns as soon as a brace-depth-0 "}" is found (without
         * consuming it), which is the closing brace of the template
         * substitution the caller is inside of; otherwise runs to the end of
         * the input.
         */
        private void scanLiveCode(boolean insideTemplateExpr) {
            int depth = 0;
            while (pos < code.length()) {
                char c = code.charAt(pos);

                if (c == '\n') {
                    atLineStart = true;
                    pos++;
                    continue;
                }

                if (c == '"' || c == '\'') {
                    skipQuoted(c);
                    continue;
                }

                if (c == '`') {
                    skipTemplate();
                    continue;
                }

                if (c == '/' && pos + 1 < code.length() && code.charAt(pos + 1) == '/') {
                    violations.add("'//' at offset " + pos);
                    pos += 2;
                    lastToken = "/";
                    atLineStart = false;
                    continue;
                }

                if (c == '/' && pos + 1 < code.length() && code.charAt(pos + 1) == '*') {
                    boolean preserved = pos + 2 < code.length() && code.charAt(pos + 2) == '!';
                    if (!preserved) {
                        violations.add("'/*' outside a preserved /*! comment at offset " + pos);
                    }
                    int close = code.indexOf("*/", pos + 2);
                    pos = close < 0 ? code.length() : close + 2;
                    lastToken = "";
                    atLineStart = false;
                    continue;
                }

                if (c == '/' && REGEX_PRECURSORS.contains(lastToken)) {
                    skipRegex();
                    continue;
                }

                if (c == '<' && code.startsWith("<!--", pos)) {
                    violations.add("'<!--' at offset " + pos);
                    pos += 4;
                    lastToken = "<";
                    atLineStart = false;
                    continue;
                }

                if (atLineStart && code.startsWith("-->", pos)) {
                    violations.add("'-->' at start of line, offset " + pos);
                    pos += 3;
                    lastToken = ">";
                    atLineStart = false;
                    continue;
                }

                if (Character.isWhitespace(c)) {
                    pos++;
                    continue;
                }

                if (c == '{') {
                    depth++;
                    lastToken = "{";
                    pos++;
                    atLineStart = false;
                    continue;
                }

                if (c == '}') {
                    if (insideTemplateExpr && depth == 0) {
                        return; // caller consumes this brace
                    }
                    depth--;
                    lastToken = "}";
                    pos++;
                    atLineStart = false;
                    continue;
                }

                if (Character.isJavaIdentifierStart(c) || Character.isDigit(c)) {
                    int j = pos;
                    while (j < code.length() && Character.isJavaIdentifierPart(code.charAt(j))) {
                        j++;
                    }
                    lastToken = code.substring(pos, j);
                    pos = j;
                } else {
                    lastToken = String.valueOf(c);
                    pos++;
                }
                atLineStart = false;
            }
        }

        private void skipQuoted(char quote) {
            pos++; // opening quote
            while (pos < code.length() && code.charAt(pos) != quote) {
                pos += code.charAt(pos) == '\\' ? 2 : 1;
            }
            pos = Math.min(pos + 1, code.length());
            lastToken = " value";
            atLineStart = false;
        }

        private void skipTemplate() {
            pos++; // opening backtick
            while (pos < code.length()) {
                char c = code.charAt(pos);
                if (c == '\\') {
                    pos += 2;
                    continue;
                }
                if (c == '`') {
                    pos++;
                    break;
                }
                if (c == '$' && pos + 1 < code.length() && code.charAt(pos + 1) == '{') {
                    pos += 2;
                    scanLiveCode(true);
                    if (pos < code.length() && code.charAt(pos) == '}') {
                        pos++;
                    }
                    continue;
                }
                pos++;
            }
            lastToken = " value";
            atLineStart = false;
        }

        private void skipRegex() {
            int j = pos + 1;
            boolean inClass = false;
            while (j < code.length()) {
                char rc = code.charAt(j);
                if (rc == '\\') {
                    j += 2;
                    continue;
                }
                if (rc == '\n') {
                    break; // unterminated - stop defensively
                }
                if (rc == '[') {
                    inClass = true;
                } else if (rc == ']') {
                    inClass = false;
                } else if (rc == '/' && !inClass) {
                    j++;
                    break;
                }
                j++;
            }
            while (j < code.length() && Character.isLetter(code.charAt(j))) {
                j++;
            }
            pos = j;
            lastToken = " value";
            atLineStart = false;
        }
    }

    /** Returns true when node refuses to parse the given source. */
    private static boolean nodeRejects(String code) throws Exception {
        return !nodeCheck(code).isEmpty();
    }

    /** Runs "node --check" and returns its combined output, empty when it parses. */
    private static String nodeCheck(String code) throws Exception {
        File temp = File.createTempFile("yui-syntax-", ".js");
        try {
            Files.write(temp.toPath(), code.getBytes(StandardCharsets.UTF_8));
            Process check = new ProcessBuilder("node", "--check", temp.getAbsolutePath())
                    .redirectErrorStream(true).start();
            String output = new String(readAll(check), StandardCharsets.UTF_8);
            return check.waitFor() == 0 ? "" : output;
        } finally {
            temp.delete();
        }
    }

    private static byte[] readAll(Process process) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = process.getInputStream().read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
