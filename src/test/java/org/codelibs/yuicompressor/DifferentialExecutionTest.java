package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * Runs each script under node twice - once as source, once compressed - and
 * asserts the two produce the same output.
 *
 * <p>This is the assertion the suite was missing. {@code node --check} proves
 * the output PARSES, never that it MEANS the same thing, and every
 * silent-corruption defect found in this release produced output that parsed:
 * "??" re-printing its operands un-munged so locals became globals, "?." being
 * deleted so a safe undefined became a TypeError, "a + +b" merging into "a++b",
 * default and rest parameters being dropped, "yield*" losing its star. A golden
 * comparison would not have caught them either, because the entire fixture
 * corpus is pre-ES6 and structurally cannot reach that syntax.
 *
 * <p>Comparing observable behaviour catches all of them without anyone having
 * to anticipate which construct will break next, which is the property that
 * makes it worth the node dependency. Each script must therefore be small,
 * self-contained and deterministic, and must print its own result - including
 * catching its own exceptions, so that "threw" is an observable outcome rather
 * than a difference in a stack trace. jquery-1.6.4.js and friends cannot
 * participate: they need a DOM.
 */
class DifferentialExecutionTest {

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

    @EnabledIf("nodeAvailable")
    @ParameterizedTest
    @ValueSource(strings = {
            // C1: "??" reached the toSource() fallback, which re-printed the
            // subtree from the original source. The parameters were munged,
            // their uses inside the operator were not, so both became globals.
            "function f(alpha, beta) { return alpha ?? beta; }\n"
                    + "console.log(f(null, 2), f(1, 2), f(undefined, 3));",
            // The same fallback deleted "?." outright: source prints undefined,
            // the old output threw a TypeError.
            "var config = {};\n"
                    + "var timeout = config.timeout ?? config.server?.timeout;\n"
                    + "console.log('timeout =', timeout);",
            // Optional-chain widening: with "a" non-null but "a.b" null,
            // "a?.b.c" must still throw while "a?.b?.c" would swallow it. The
            // difference is only observable at runtime.
            "var a = { b: null };\n"
                    + "try { console.log(a?.b.c); } catch (e) { console.log('threw', e.constructor.name); }",
            // Logical assignment, all three: same fallback, same un-munging.
            "function f(alpha, beta) { alpha ||= beta; return alpha; }\n"
                    + "console.log(f(0, 7), f(5, 7));",
            "function f(alpha, beta) { alpha &&= beta; return alpha; }\n"
                    + "console.log(f(0, 7), f(5, 7));",
            "function f(alpha, beta) { alpha ??= beta; return alpha; }\n"
                    + "console.log(f(null, 7), f(5, 7));",
            "function f(alpha, beta) { alpha **= beta; return alpha; }\n"
                    + "console.log(f(2, 10));",
            // Operator merges: "a+ +b" losing its space reads as "a++b", and
            // "a- -b" as "a--b". Both change meaning or fail to parse.
            "var a = 1, b = 2;\nconsole.log(a + +b, a - -b, a + + +b);",
            // Default and rest parameters were dropped entirely, changing
            // f() from 1 to undefined and f.length with it.
            "function f(a=1){ return a; }\nconsole.log(f(), f(5), f.length);",
            "function f(...args){ return args.length; }\nconsole.log(f(1,2,3), f(), f.length);",
            "function f(a, ...rest){ return a + ':' + rest.join(','); }\nconsole.log(f(1,2,3));",
            // A default that reads an earlier parameter: the expression is live
            // code and its identifiers must be munged consistently with the
            // declaration they refer to.
            "function f(alpha, beta=alpha*2){ return beta; }\nconsole.log(f(3), f(3, 1));",
            // "yield*" delegates; dropping the star yields the generator object
            // once instead.
            "function* inner(){ yield 1; yield 2; }\n"
                    + "function* outer(){ yield* inner(); yield 3; }\n"
                    + "var out = [], it = outer(), r = it.next();\n"
                    + "while (!r.done) { out.push(r.value); r = it.next(); }\n"
                    + "console.log(out.join(','));",
            // Destructuring parameters, which must round-trip exactly.
            "function f([a,b]){ return a+b; }\nconsole.log(f([1,2]));",
            "function f({b}){ return b; }\nconsole.log(f({b: 7}));",
            // Redundant double braces were semantically neutral, so this is a
            // guard rather than a reproduction.
            "var out = [];\nfor (var i=0;i<3;i++) { out.push(i); }\nconsole.log(out.join(','));",
            // A labelled break out of a nested loop, whose braces the same
            // change touched.
            "var out = [];\nouter: for (var i=0;i<3;i++) { for (var j=0;j<3;j++) { if (j===1) continue outer; out.push(i+'-'+j); } }\n"
                    + "console.log(out.join(','));",
            // Generator object methods used to crash the compressor outright.
            "var o = { *gen(){ yield 1; yield 2; } };\n"
                    + "var out = [], it = o.gen(), r = it.next();\n"
                    + "while (!r.done) { out.push(r.value); r = it.next(); }\n"
                    + "console.log(out.join(','));",
            // Shorthand properties are the identifier used as BOTH key and
            // binding, in an object literal and in a destructuring pattern.
            // Munging one renames the key with it.
            "function f(){ var longLocalName = 7; return { longLocalName }; }\n"
                    + "console.log(JSON.stringify(f()));",
            "function f(){ var o = { b: 7 }; var { b } = o; return b; }\nconsole.log(f());",
            // eval and with must keep seeing the locals they can name.
            "function f(){ var secretName = 42; return eval('secretName'); }\nconsole.log(f());",
            "function f(obj){ var x = 5; with (obj) { return x; } }\nconsole.log(f({}), f({x: 9}));" })
    void compressedScriptBehavesLikeItsSource(String source) throws Exception {
        assertSameBehaviour(source, compress(source, -1));
    }

    /**
     * The same comparison at {@code --line-break 20}. A break landing inside a
     * token is the C2 defect, and its identifier-splitting variant produces
     * output that still parses - so only running it can tell the two apart.
     */
    @EnabledIf("nodeAvailable")
    @ParameterizedTest
    @ValueSource(strings = { "var out = [];\nvar q = 1 + + +function(){ out.push('abcdefghijklmnop'); }();\n"
            + "console.log(out.join(','));",
            "var out = [];\nvar q = 1 + + +function(){ var s = 'hello'; out.push(s); }();\n"
                    + "console.log(out.join(','));",
            "var alpha=1; var beta=2; var gamma=alpha+beta; console.log(alpha, beta, gamma);" })
    void compressedScriptBehavesLikeItsSourceWithLineBreaks(String source) throws Exception {
        assertSameBehaviour(source, compress(source, 20));
    }

    private void assertSameBehaviour(String source, String compressed) throws Exception {
        String fromSource = run(source);
        String fromCompressed = run(compressed);
        assertEquals(fromSource, fromCompressed,
                "compressed output does not behave like its source.\nsource:\n" + source + "\ncompressed:\n"
                        + compressed);
    }

    private String compress(String source, int linebreakpos) throws IOException {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT)
                .compress(out, linebreakpos, true, false, false, false);
        return out.toString();
    }

    /**
     * Runs a script under node and returns its stdout plus exit status. The
     * exit status is part of the comparison so that a compressed script which
     * throws where its source did not is a difference; stderr is not, because a
     * stack trace carries file names and line numbers that legitimately differ.
     */
    private String run(String code) throws Exception {
        File temp = File.createTempFile("yui-diff-", ".js");
        try {
            Files.write(temp.toPath(), code.getBytes(StandardCharsets.UTF_8));
            Process node = new ProcessBuilder("node", temp.getAbsolutePath()).start();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = node.getInputStream().read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            int status = node.waitFor();
            return "exit=" + status + "\n" + new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            temp.delete();
        }
    }
}
