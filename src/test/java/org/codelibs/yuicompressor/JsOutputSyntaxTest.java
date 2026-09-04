package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
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

    /**
     * Skips one fixture when node is absent, rather than disabling the whole
     * method with {@code @EnabledIf}, which hid 10 real node --check
     * executions behind a single skip line. {@link NodeRuntime#isAvailable()}
     * throws rather than returning false when node is present but broken, so a
     * sandboxed or hanging node fails the build instead of quietly disabling
     * this guard.
     */
    private static void requireNode() {
        Assumptions.assumeTrue(NodeRuntime.isAvailable(), "node is not on PATH; this fixture was not checked");
    }

    static Stream<String> fixtures() throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".js"))
                    .sorted()
                    .collect(Collectors.toList())
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void compressedOutputParses(String fixture) throws Exception {
        requireNode();
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
    // sequences is closed at four members; this guards all four, everywhere
    // the scanner can tell they are live code rather than string/template/
    // regex content or an already-real comment (see CommentInjectionScanner's
    // own javadoc for what "can tell" rests on, and its known limits).
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

    @Test
    public void scannerCatchesHtmlCommentAfterDivisionFollowingEmptyBlock() {
        // "{}"  is reachable as ordinary code, not just as an object literal:
        // "var f = {} / a;" compresses to "var f={}/a;". A "/" following "}"
        // must be treated as division, not as opening a regex - otherwise the
        // scanner reads everything up to the next "/" as inert regex body and
        // never checks it, silently swallowing whatever is in between.
        assertEquals(1, new CommentInjectionScanner("f={}/a<!--INJECT-->b/c;").scan().size());
    }

    @Test
    public void scannerAllowsRegexLiteralFollowingEmptyBlockStatement() {
        // The competing, also-reachable case: "if(x){} /re/.test(y);"
        // compresses to "if(x){}/re/.test(y);" - a genuine regex literal
        // right after a block. Treating "}" as division-context must not
        // turn this into a false positive: "re/.test(y);" scanned as
        // ordinary code contains none of the four dangerous sequences.
        assertEquals(List.of(), new CommentInjectionScanner("if(x){}/re/.test(y);").scan());
    }

    @Test
    public void scannerCatchesHtmlCommentAfterDivisionFollowingPostfixIncrement() {
        // Same shape as the "{}" case, found by the same audit: "var b = a++
        // / 2;" compresses to "var b=a++/2;" - postfix "++"/"--" completes a
        // value, so the following "/" is unambiguously division, never a
        // regex start. Folding "++" into a plain "+" token (as any other
        // compound operator ending in "+" would be) wrongly treats it as a
        // regex precursor and swallows whatever follows as inert regex body.
        assertEquals(1, new CommentInjectionScanner("b=a++/x<!--INJECT-->y/z;").scan().size());
        assertEquals(1, new CommentInjectionScanner("b=a--/x<!--INJECT-->y/z;").scan().size());
    }

    @Test
    public void scannerAllowsDivisionFollowingPostfixIncrement() {
        assertEquals(List.of(), new CommentInjectionScanner("var b=a++/2;").scan());
        assertEquals(List.of(), new CommentInjectionScanner("var b=a--/2;").scan());
    }

    @Test
    public void scannerAllowsRegexLiteralFollowingPrefixIncrement() {
        // Unlike postfix, prefix "++"/"--" immediately before a regex is
        // real, reachable compressor output: "var a=1; ++/x/.test(a);"
        // compresses to itself unchanged (confirmed against the actual
        // compressor - neither node nor Rhino treats it as a syntax error;
        // prefix "++" only requires a syntactic LeftHandSideExpression
        // operand). Scanning "/x/.test(a)" as ordinary code here, rather than
        // recognizing the real regex and skipping it whole, is on the safe
        // side of the documented asymmetry - it costs nothing when, as here,
        // that span holds none of the four dangerous sequences.
        assertEquals(List.of(), new CommentInjectionScanner("var a=1;++/x/.test(a);").scan());
    }

    @Test
    public void scannerCatchesHtmlCommentAfterDivisionFollowingIdentifierNamedOf() {
        // "of" is a contextual keyword, not a reserved word - it is a
        // perfectly ordinary identifier everywhere outside a for-of head, and
        // JavaScriptCompressor's own munged-name pool already treats it that
        // way (twos.remove("of") is filed under "ES6+ two-letter keywords",
        // separate from the genuinely reserved "as"/"is"/"do"/"if"/"in"
        // above it - it is excluded only so the pool never *hands out* "of"
        // as a new munged name, not because using it as one is illegal).
        // "var of = 4; var x = of / 2;" compresses to "var of=4;var x=of/2;"
        // - same reachable shape as "}" and postfix "++"/"--".
        assertEquals(1, new CommentInjectionScanner("x=of/y<!--INJECT-->z/w;").scan().size());
    }

    @Test
    public void scannerAllowsDivisionAfterIdentifierNamedOf() {
        assertEquals(List.of(), new CommentInjectionScanner("var of=4;var x=of/2;").scan());
    }

    @Test
    public void scannerFlagsEscapedSlashInRegexAfterBlockAsAcceptedTradeoff() {
        // NOT A BUG - an accepted, deliberate false positive, kept under test
        // so a future contributor who sees it fire does not "fix" it by
        // putting "}" back into REGEX_PRECURSORS and silently reopening the
        // false NEGATIVE that scannerCatchesHtmlCommentAfterDivisionFollowingEmptyBlock
        // guards against.
        //
        // "if (x) { y = 1; } /foo\/\//.test(z);" compresses to
        // "if(x){y=1;}/foo\/\//.test(z);". With "}" excluded from
        // REGEX_PRECURSORS, the "/" opening that regex is read as division,
        // so the regex's own content - including its escaped "\/" pairs - is
        // scanned as ordinary code instead of skipped whole. Walking
        // "foo\/\//"  character by character, the second escaped slash's
        // literal "/" ends up immediately before the regex's real closing
        // "/", which the scanner reads as a live "//". A false positive costs
        // one investigation (confirm the flagged offset sits inside a
        // genuine regex, as it does here); a false negative in this
        // direction ships a Critical. Special-casing escaped slashes inside
        // an accidentally-scanned regex would remove this trade, but adds
        // real scanner complexity for a test-only guard - rejected in favor
        // of documenting and testing the trade explicitly.
        assertEquals(1, new CommentInjectionScanner("if(x){y=1;}/foo\\/\\//.test(z);").scan().size());
    }

    @Test
    public void scannerAllowsRegexLiteralAfterForOf() {
        // The competing, syntactically legitimate case ("for (x of /re/)") -
        // not observed merged in real generator output (it always keeps a
        // space: "for(var x of /re/g.exec(s)){...}"), but kept as a
        // regression guard against a future generator change producing the
        // merged form. Treating "of" as division-context must not turn this
        // into a false positive: scanned as ordinary code, "re/g.exec(s))"
        // contains none of the four dangerous sequences.
        assertEquals(List.of(), new CommentInjectionScanner("for(x of/re/g.exec(s)){}").scan());
    }

    /**
     * Scans compressed JavaScript for the four comment-injection sequences,
     * everywhere they would actually be read as live code. Occurrences inside
     * string, template, and regex literals are inert text and are skipped, as
     * is the interior of a preserved "/*!" banner comment (already a real
     * comment, so embedded text there is harmless).
     *
     * <p>Distinguishing a regex literal from a division operator - the
     * classic JS lexing ambiguity - uses the standard heuristic of looking at
     * the preceding significant token, which is not a general-purpose lexer
     * and is not guaranteed to classify every token sequence correctly. The
     * two directions of mistake are NOT equally safe. Misclassifying an
     * actual regex precursor as division-context only means a real regex
     * literal's content gets scanned as ordinary code instead of skipped
     * whole - at worst a false positive, e.g. if that content happened to
     * contain "//" inside a "[...]" character class, which is a one-time
     * investigation. Misclassifying actual division as a regex precursor is
     * the dangerous direction: {@link #skipRegex} then treats everything up
     * to the next unescaped "/" as inert regex body without scanning it at
     * all, so any of the four sequences hiding in that span is a genuine
     * false negative, not merely a missed boundary. REGEX_PRECURSORS is
     * curated to avoid the dangerous direction wherever a token that
     * completes a value (rather than awaiting an operand) was found reachable
     * immediately before "/" in real generator output - "}" (e.g.
     * "var f = {} / a;"), postfix "++"/"--" (e.g. "var b = a++ / 2;"), and
     * the contextual keyword "of" used as an ordinary identifier (e.g.
     * "var of = 4; var x = of / 2;") are the three found so far, each
     * confirmed reachable and each with its own regression test - plus one
     * accepted, deliberate false positive on the "}" side (see
     * {@link JsOutputSyntaxTest#scannerFlagsEscapedSlashInRegexAfterBlockAsAcceptedTradeoff}).
     * This is a curated allowlist, not a derivation from the grammar, so it
     * is not a proof that no further case exists.
     */
    private static final class CommentInjectionScanner {

        // "}" and "of" are deliberately absent, unlike every other entry
        // here. Both are reachable immediately before a genuine division, not
        // just before their "textbook" regex-precursor use (a block statement
        // followed by a regex literal; a for-of loop's regex-valued
        // iterable):
        //   "var f = {} / a;"      -> "var f={}/a;"
        //   "var of = 4; var x = of / 2;" -> "var of=4;var x=of/2;"
        // "of" is only a contextual keyword - an ordinary identifier
        // everywhere outside a for-of head - unlike the reserved words below
        // it; JavaScriptCompressor's own munged-name pool already treats it
        // that way (twos.remove("of"), filed separately as "ES6+ two-letter
        // keywords" from the genuinely reserved words above it).
        // skipRegex() cannot tell a real precursor use apart from one of
        // these division uses, so misclassifying either as a regex precursor
        // would make it swallow whatever follows the division as inert
        // "regex body" up to the next "/", including any of the four
        // dangerous sequences hiding in it - a missed injection, not just a
        // missed regex boundary. Treating them as division-context instead
        // only costs the rarer case (a regex literal actually following a
        // block statement, or actually iterated by a for-of) being scanned as
        // ordinary code, which is safe: it just means that regex's own
        // content is checked too.
        private static final Set<String> REGEX_PRECURSORS = Set.of("(", ",", "=", ":", "[", "!", "&", "|", "?", "{",
                ";", "+", "-", "*", "%", "<", ">", "^", "~", "return", "typeof", "instanceof", "in", "new", "delete",
                "void", "throw", "yield", "case", "do", "else");

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

                // "++"/"--" get their own two-character token, deliberately
                // NOT added to REGEX_PRECURSORS. Every other compound operator
                // this tokenizer folds into its last character ("===", "&&",
                // "=>", ...) is still awaiting an operand afterward, so
                // treating a following "/" as a regex start stays correct
                // regardless of which one it was. Postfix "++"/"--" is the
                // one exception: "a++/2" is reachable ("var b = a++ / 2;"
                // compresses to it) and unambiguously division - postfix
                // "++"/"--" completes a value, it can never itself be
                // followed by the start of a new primary expression such as a
                // regex literal in valid JS. Folding it into lastToken "+" or
                // "-" like any other operator would wrongly mark the "/"
                // after it as a regex precursor, the same false-negative
                // shape as "}" above.
                //
                // This branch does not distinguish postfix from prefix - both
                // land on lastToken "++"/"--". That is fine, but NOT because
                // prefix use cannot be followed by "/": it demonstrably can -
                // "++/x/.test(a)" is neither a node --check syntax error nor
                // one Rhino rejects (prefix "++" only requires a syntactic
                // LeftHandSideExpression operand, e.g. a CallExpression,
                // which a regex literal followed by a member/call
                // unremarkably is; whether that expression is actually
                // assignable is a separate, later check that this compressor
                // reproduces rather than performs). It is real, reachable
                // compressor output: "var a=1; ++/x/.test(a);" compresses
                // to itself unchanged. Excluding "++"/"--" from
                // REGEX_PRECURSORS is still correct for this case, for a
                // different reason than the postfix one above: it puts the
                // following "/" on the SAFE side of the asymmetry documented
                // on this class - scanning "/x/.test(a)" as ordinary code
                // instead of skipping it whole as a regex costs nothing here
                // (that span holds none of the four dangerous sequences), and
                // in general costs at most a false positive, never a missed
                // injection. So the one-token lookback being unable to tell
                // prefix from postfix here does not matter: both land on the
                // direction of misclassification that is safe to make.
                if ((c == '+' || c == '-') && pos + 1 < code.length() && code.charAt(pos + 1) == c) {
                    lastToken = c == '+' ? "++" : "--";
                    pos += 2;
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
            lastToken = " value";
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
            lastToken = " value";
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
            lastToken = " value";
            atLineStart = false;
        }
    }

    /** Returns true when node refuses to parse the given source. */
    private static boolean nodeRejects(String code) throws Exception {
        return !nodeCheck(code).isEmpty();
    }

    /**
     * Runs "node --check" and returns its combined output, empty when it
     * parses. Bounded and redirected to a file by {@link NodeRuntime}, so a
     * hanging node fails rather than stalling the build.
     */
    private static String nodeCheck(String code) throws Exception {
        return NodeRuntime.check(code);
    }
}
