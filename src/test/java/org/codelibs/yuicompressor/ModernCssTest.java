package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import com.yahoo.platform.yui.compressor.CssCompressor;

/** Regression tests for modern CSS syntax. */
class ModernCssTest {

    private String compress(String source) throws Exception {
        return compress(source, -1);
    }

    private String compress(String source, int linebreakpos) throws Exception {
        StringWriter out = new StringWriter();
        new CssCompressor(new StringReader(source)).compress(out, linebreakpos);
        return out.toString().trim();
    }

    @Test
    void containerNameKeepsSpaceBeforeCondition() throws Exception {
        String result = compress("@container card (min-width: 400px) { .t { color: red } }");
        assertTrue(result.contains("card ("),
                "an identifier followed directly by '(' becomes a function token: " + result);
    }

    @Test
    void supportsNotKeepsSpaceBeforeCondition() throws Exception {
        String result = compress("@supports (display: grid) and (not (display: inline-grid)) { .g { color: red } }");
        assertTrue(result.contains("not ("),
                "'not(' would be parsed as a function token: " + result);
    }

    @Test
    void scopeToKeepsSpaceBeforeCondition() throws Exception {
        String result = compress("@scope (.a) to (.b) { .c { color: red } }");
        assertTrue(result.contains("to ("),
                "'to(' would be parsed as a function token: " + result);
    }

    @Test
    void mediaQueryKeywordIsLowercased() throws Exception {
        String result = compress("@media screen AND (min-width: 400px) { .m { color: red } }");
        assertEquals("@media screen and (min-width:400px){.m{color:red}}", result);
    }

    @Test
    void customPropertyColorIsNotRewritten() throws Exception {
        assertEquals(":root{--main-color:#ff0000}", compress(":root { --main-color: #ff0000; }"));
    }

    @Test
    void customPropertyInnerWhitespaceIsPreserved() throws Exception {
        String result = compress(":root { --txt: hello  world; }");
        assertTrue(result.contains("hello  world"),
                "a custom property value is a token stream and must be kept verbatim: " + result);
    }

    @Test
    void propertyAtRuleInitialValueIsNotRewritten() throws Exception {
        String result = compress("@property --c { syntax: '<color>'; inherits: false; initial-value: #ff0000; }");
        assertTrue(result.contains("#ff0000"),
                "an @property initial-value must be kept verbatim: " + result);
    }

    @Test
    void ordinaryColorIsStillOptimised() throws Exception {
        assertEquals("a{color:red}", compress("a { color: #ff0000; }"));
    }

    @Test
    void customPropertyKeywordCaseIsNotFolded() throws Exception {
        assertEquals(":root{--my-op:NOT(x)}", compress(":root { --my-op: NOT(x); }"));
    }

    @Test
    void commentMentioningPropertyDoesNotSwallowFollowingRule() throws Exception {
        String result = compress("/* TODO use @property here */\na { color: #ff0000; }");
        assertEquals("a{color:red}", result);
        assertTrue(result.indexOf("___YUICSSMIN") < 0,
                "no internal placeholder should ever leak into the output: " + result);
    }

    @Test
    void propertyAtRuleDescriptorStringContainingBraceDoesNotMisleadBlockScan() throws Exception {
        String result = compress("@property --c { syntax: \"}\"; inherits: false; initial-value: #ff0000; }");
        assertTrue(result.contains("#ff0000"),
                "a '}' inside a quoted descriptor value must not be read as the block's closing brace: " + result);

        String result2 = compress("@property --c { syntax: '{'; inherits: false; initial-value: #ff0000; }");
        assertTrue(result2.contains("#ff0000"),
                "a '{' inside a quoted descriptor value must not be read as a nested block: " + result2);
    }

    @Test
    void emptyLayerDeclarationIsKept() throws Exception {
        String result = compress("@layer utilities {} .a { color: red }");
        assertTrue(result.contains("@layer utilities"),
                "an empty @layer still declares layer order and must be kept: " + result);
    }

    @Test
    void emptyPlainRuleIsStillRemoved() throws Exception {
        assertEquals("a{color:red}", compress(".empty {} a { color: red }"));
    }

    @Test
    void modernAtRuleNameIsLowercased() throws Exception {
        String result = compress("@LAYER base { a { color: red } }");
        assertTrue(result.startsWith("@layer"), "the at-rule name should be lowercased: " + result);
    }

    @Test
    void emptyMediaBlockIsStillRemoved() throws Exception {
        assertEquals("a{color:red}", compress("@media screen {} a { color: red }"));
    }

    @Test
    void compressedOutputNeverLeaksInternalPlaceholder() throws Exception {
        String[] inputs = {
                "a { content: 'a string value' }",
                "a { background: url(data:image/png;base64,iVBORw0KGgo=) }",
                "a { width: calc(100% - 10px) }",
                "/*! preserved comment */\na { color: red }",
                "/* ordinary comment */\na { color: red }",
                ":root { --main-color: #ff0000; }",
                "@property --c { syntax: '<color>'; inherits: false; initial-value: #ff0000; }",
        };
        for (String input : inputs) {
            String result = compress(input);
            assertTrue(result.indexOf("___YUICSSMIN") < 0,
                    "no internal placeholder should ever leak into the output for input [" + input + "]: " + result);
        }
    }

    @Test
    void zeroTimeValuesKeepTheirUnit() throws Exception {
        // CSS Values and Units Level 3 allows omitting the unit only for zero
        // <length>. <time> has no such exemption, so "0s" must not become "0".
        // zeros.css.min expects otherwise; that golden encodes invalid CSS and is
        // deliberately left quarantined rather than matched.
        String result = compress("a { transition-duration: 0s; transition-delay: 0ms; }");
        assertTrue(result.contains("0s"), "a zero <time> must keep its unit: " + result);
        assertTrue(result.contains("0ms"), "a zero <time> must keep its unit: " + result);
    }

    @Test
    void zeroLengthValuesStillDropTheirUnit() throws Exception {
        // The <length> exemption does apply, so this optimisation must be kept.
        assertEquals("a{width:0;height:0}", compress("a { width: 0%; height: 0px; }"));
    }

    // The at-rule and declaration matchers below all used to fire on their
    // literal text wherever it appeared, with no regard for whether an at-rule
    // or declaration could actually start there. Requiring a real boundary
    // ("{", "}", ";", the start of the stylesheet, or a preserved-token
    // placeholder) closes the whole class; each test names one instance.

    @Test
    void atPropertyTextInsideAUrlDoesNotDisableTheFollowingRule() throws Exception {
        // "@property" was located context-free, so this match preserved
        // everything up to the next balanced "}" verbatim - which meant the
        // rule AFTER it was emitted completely unminified.
        String result = compress("a { background: url(/img/@property.png) } b { color: #ff0000; margin: 0px }");
        assertEquals("a{background:url(/img/@property.png)}b{color:red;margin:0}", result, result);
    }

    @Test
    void atDirectiveTextInsideAUrlKeepsItsCase() throws Exception {
        // The at-directive lowercasing pass was context-free too. URL paths are
        // case-sensitive on essentially every server, so rewriting one is a
        // broken stylesheet, not a smaller one.
        String result = compress("a { background: url(/img/@MEDIA.png) } B { COLOR: #ff0000 }");
        assertEquals("a{background:url(/img/@MEDIA.png)}B{COLOR:red}", result, result);
    }

    @Test
    void atDirectiveAfterAPreservedCommentIsStillLowercased() throws Exception {
        // The boundary check must not lose the legitimate case: after comment
        // preservation an at-rule can be preceded by a placeholder rather than
        // by "{", "}" or ";".
        String result = compress("/*! keep */@MEDIA screen{b{color:#00ff00}}");
        assertEquals("/*! keep */@media screen{b{color:#0f0}}", result, result);
    }

    @Test
    void atCharsetTextInsideAUrlIsNotHoistedToTheTop() throws Exception {
        String result = compress("a { background: url(/x/@charset \"y\";) } b{color:#ff0000}");
        assertEquals("a{background:url(/x/@charset \"y\";)}b{color:red}", result, result);
    }

    @Test
    void aRealAtCharsetIsStillHoistedAndLowercased() throws Exception {
        assertEquals("@charset \"utf-8\";a{color:red}", compress("@charset \"utf-8\"; a { color: #ff0000 }"));
    }

    @Test
    void customPropertyValueIsPreservedAfterAPreservedComment() throws Exception {
        // The declaration matcher accepted only "{" or ";" as the preceding
        // character, so a preserved "/*!" banner between the "{" and the
        // declaration made the value ordinary again and the colour optimiser
        // rewrote it.
        String result = compress(":root{/*! v1 */--brand:#ff0000}");
        assertEquals(":root{/*! v1 */--brand:#ff0000}", result, result);
    }

    @Test
    void customPropertyUnitIsPreservedAfterAPreservedComment() throws Exception {
        // The damaging half of the same defect: calc(var(--pad) + 1px) needs
        // the unit, and "0" is not a length there.
        String result = compress(":root{/*! x */--pad:0px}");
        assertEquals(":root{/*! x */--pad:0px}", result, result);
    }

    @Test
    void aDoubleDashInsideAValueIsStillNotTreatedAsACustomProperty() throws Exception {
        // The boundary check must keep excluding this: "--2px" here is a value,
        // not a declaration.
        assertEquals("a{width:calc(1px - -2px)}", compress("a{width:calc(1px --2px)}"));
    }

    // Only a preserved COMMENT placeholder forms a boundary. Three placeholder
    // forms exist by the time these passes run - comment, quoted string, bare -
    // and the other two are not places a declaration or at-rule can begin.
    // Accepting the quoted form reintroduced the @property defect through a
    // different door, which is what these two pin down.

    @Test
    void aPreservedStringDoesNotMakeTheFollowingTextAnAtRule() throws Exception {
        // With a quoted placeholder accepted as a boundary, the "@property"
        // here was read as an at-rule and everything to the next balanced "}"
        // preserved - leaving rule "b" unminified, exactly the I2 symptom.
        String result = compress("a{background:url(/x/\"y\"@property.png)}b{color:#ff0000;margin:0px}");
        assertEquals("a{background:url(/x/\"y\"@property.png)}b{color:red;margin:0}", result, result);
    }

    @Test
    void aPreservedStringDoesNotStartACustomPropertyDeclaration() throws Exception {
        // A string abutting a declaration is not valid CSS, so this "--y" is
        // not a custom property and its value is not preserved.
        String result = compress("a{content:\"x\"--y:0px}b{color:#ff0000}");
        assertEquals("a{content:\"x\"--y:0}b{color:red}", result, result);
    }

    @Test
    void anOrdinaryCommentBeforeACustomPropertyIsUnaffected() throws Exception {
        // Routine CSS that never had the defect and must not acquire it: an
        // ordinary comment is deleted whole, delimiters included, so the "{"
        // ends up directly adjacent to the declaration and the boundary test
        // is never consulted.
        assertEquals(":root{--brand:#ff0000;--pad:0px}",
                compress(":root{/* brand colour */--brand:#ff0000;--pad:0px}"));
    }

    @Test
    void theIe7EmptyCommentHackStillSurvives() throws Exception {
        // The other comment-shaped placeholder, kept as a guard on the
        // narrowed detector.
        assertEquals("html>/**/body{color:red}", compress("html >/**/ body{color:#ff0000}"));
    }

    // Comment collection is the FIRST pass, so it has to understand CSS
    // structure itself. It used to be a bare indexOf("/*") scan, which meant a
    // comment-looking span inside a string or an unquoted url() became a
    // placeholder sitting mid-value while looking exactly like a leading
    // banner comment. That defeated the boundary predicate at all four call
    // sites - a placeholder's shape records how it was created, never where it
    // sits, so narrowing the predicate again could not have fixed it.

    @Test
    void aCommentInsideAUrlDoesNotMakeTheFollowingTextAnAtRule() throws Exception {
        String result = compress("a{background:url(/x/*!k*/@property.png)}b{color:#ff0000;margin:0px}");
        assertEquals("a{background:url(/x/*!k*/@property.png)}b{color:red;margin:0}", result, result);
    }

    @Test
    void aCommentInsideAUrlDoesNotExposeTheUrlToAtDirectiveLowercasing() throws Exception {
        assertEquals("a{background:url(/x/*!k*/@MEDIA.png)}", compress("a{background:url(/x/*!k*/@MEDIA.png)}"));
    }

    @Test
    void aCommentInsideAUrlDoesNotLetAtCharsetEscapeTheUrl() throws Exception {
        // The worst of the four: the stylesheet's declared encoding was
        // invented from a URL fragment, and the fragment deleted from the URL.
        String result = compress("a{background:url(/x/*!k*/@charset \"y\";.png)}b{color:#ff0000}");
        assertEquals("a{background:url(/x/*!k*/@charset \"y\";.png)}b{color:red}", result, result);
    }

    @Test
    void aCommentInsideAUrlDoesNotStartACustomPropertyDeclaration() throws Exception {
        // The rule after it must minify, which it did not while the url's
        // contents were being preserved as a custom property value.
        String result = compress("a{background:url(/x/*!k*/--y:1px)}b{color:#ff0000;margin:0px}");
        assertEquals("a{background:url(/x/*!k*/--y:1px)}b{color:red;margin:0}", result, result);
    }

    // The same context-free scan, with no closing delimiter, replaced
    // everything to end-of-input with a marker the later "kill the comment"
    // pass could not match - truncating the stylesheet, dropping the following
    // rules, and emitting internal scaffolding, all with a success exit code.

    @Test
    void anUnbalancedCommentOpenerInsideAStringDoesNotTruncateTheStylesheet() throws Exception {
        // content:"/*" is valid CSS and was enough to trigger it.
        String result = compress("a{content:\"/*\"}b{color:#ff0000;margin:0px}");
        assertEquals("a{content:\"/*\"}b{color:red;margin:0}", result, result);
    }

    @Test
    void aCommentOpenerInsideAUrlDoesNotPairWithAnUnrelatedLaterCloser() throws Exception {
        String result = compress("a{background:url(/img/*/thumb.png)}b{color:#ff0000}  c{content:\"*/\"}");
        assertEquals("a{background:url(/img/*/thumb.png)}b{color:red}c{content:\"*/\"}", result, result);
    }

    @Test
    void anUnterminatedCommentOpenerInsideADataUrlIsNotAComment() throws Exception {
        // The realistic trigger for the truncation: a data URL can carry
        // arbitrary bytes, and this one previously matched a "/*" that had no
        // closer, taking the rest of the stylesheet with it. Being inside a
        // url() token, it is not a comment at all, so it does not even reach
        // the unterminated-comment error.
        String result = compress("a{background:url(data:text/plain,/*unterminated)}b{color:#ff0000}");
        assertEquals("a{background:url(data:text/plain,/*unterminated)}b{color:red}", result, result);
    }

    @Test
    void aBalancedCommentLikeStringStillRoundTrips() throws Exception {
        // This always worked; it is the control that says the fix did not
        // change the case that was already correct.
        assertEquals("a{content:\"/* */\"}b{color:red}", compress("a{content:\"/* */\"}b{color:#ff0000}"));
    }

    @Test
    void aGenuinelyUnterminatedCommentIsRejectedRatherThanGuessedAt() throws Exception {
        // Browsers consume such a comment to end-of-input, so the stylesheet is
        // already broken for the author either way. Reproducing that here would
        // mean silently discarding the rest of the file with exit 0, which is
        // the corruption this pass exists to stop.
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> compress("a{color:red} /* oops"));
        assertTrue(failure.getMessage().contains("unterminated CSS comment"), failure.getMessage());
    }

    @Test
    void aRealCommentIsStillCollectedAndRemoved() throws Exception {
        // The control for the whole rewrite: ordinary comments must still be
        // found and dropped, and preserved banners still kept.
        assertEquals("a{color:red}", compress("a{/* note */color:#ff0000}"));
        assertEquals("/*! keep */a{color:red}", compress("/*! keep */a{color:#ff0000}"));
    }

    @Test
    void anIdentifierEndingInUrlIsNotTreatedAsAUrlToken() throws Exception {
        // "myurl(" must not be skipped as a url token, or a comment after it
        // would be missed.
        assertEquals("a{background:myurl(x)}b{color:red}",
                compress("a{background:myurl(x)}/* note */b{color:#ff0000}"));
    }

    // url("...") is a <function-token>, not a url-token (CSS Syntax L3 4.3.4), so its
    // contents are ordinary tokens and a comment among them is an ordinary comment.
    // Raw-scanning it for ")" desynced the collector and emitted an unterminated "/*".

    @Test
    void aCommentInsideAQuotedUrlDoesNotLeaveAnUnterminatedCommentOpener() throws Exception {
        // The comment holds a ")" and then a second "/*". The raw scan stopped at that
        // inner ")", resumed inside the comment, and deleted the only "*/" in the file,
        // so a browser discarded .nav and .footer. Exit code was 0.
        String result = compress(".hero { background-image: url(\"a.png\" /* legacy: url(b.png) /* keep */); }\n"
                + ".nav { color: #ff0000; }\n.footer { margin: 0px; }");
        assertEquals(".hero{background-image:url(\"a.png\")}.nav{color:red}.footer{margin:0}", result, result);
        assertTrue(result.indexOf("/*") < 0, "an unterminated comment opener survived: " + result);
    }

    @Test
    void aQuotedUrlCarryingAQuoteInsideItsCommentDoesNotFailTheBuild() throws Exception {
        // Valid CSS. The phantom string opened by the '"' inside the comment swallowed
        // the real "*/", so the unterminated-comment throw fired on a legal stylesheet.
        assertEquals("a{background:url(\"x\")}b{content:\"/*\"}",
                compress("a{background:url(\"x\" /* ) \" */)}b{content:\"/*\"}"));
        assertEquals("@import url(\"a.css\");b{content:\"/*\"}",
                compress("@import url(\"a.css\" /* ) \" */);b{content:\"/*\"}"));
    }

    @Test
    void aCommentInsideAQuotedUrlIsCollectedAndRemoved() throws Exception {
        assertEquals("a{background:url(\"x.png\")}b{color:red}",
                compress("a{background:url(\"x.png\" /* don't ship me */)}b{color:#ff0000}"));
        assertEquals("a{background:url('x.png')}b{color:red}",
                compress("a{background:url('x.png' /* n */)}b{color:#ff0000}"));
    }

    @Test
    void whitespaceBetweenUrlAndItsQuoteStillMakesItAFunctionToken() throws Exception {
        // 4.3.4 skips whitespace before deciding, and the match is case-insensitive.
        assertEquals("a{background:url(\"x.png\")}b{color:red}",
                compress("a{background:url(  \n  \"x.png\" /* n */ )}b{color:#ff0000}"));
        assertEquals("a{background:url('x.png')}b{color:red}",
                compress("a{background:Url( 'x.png' /* n */)}b{color:#ff0000}"));
    }

    @Test
    void aCommentLikeSpanInsideAnUnquotedUrlIsStillNotAComment() throws Exception {
        // The other half of the split: only the unquoted form is raw. Scanning this one
        // by the spec's bad-url rule instead - stopping at the ")" inside the attribute -
        // resumes the collector inside the URL and deletes "/*x*/" from it.
        String source = "a{background:url(data:image/svg+xml,<svg xmlns=\"a)b/*x*/c\"/>)}d{color:#ff0000}";
        String result = compress(source);
        assertTrue(result.contains("/*x*/"), "URL bytes were deleted as a comment: " + result);
        assertEquals("a{background:url(data:image/svg+xml,<svgxmlns=\"a)b/*x*/c\"/>)}d{color:red}", result, result);
    }

    @Test
    void anUnterminatedCommentInsideAQuotedUrlIsRejectedRatherThanEmitted() throws Exception {
        // Now that the quoted form is scanned normally, this reaches the loud path. It
        // used to be emitted verbatim, leaving an unterminated "/*" in shippable CSS
        // with exit code 0 - the corruption the throw exists to prevent.
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> compress("a{background:url(\"x\" /* oops)}b{color:#ff0000}"));
        assertTrue(failure.getMessage().contains("unterminated CSS comment"), failure.getMessage());
    }

    // A span captured by preserveToken is restored verbatim at the very end, after the
    // loop that resolves candidate comment markers has run, so a comment inside one was
    // emitted as internal scaffolding.

    @Test
    void aCommentInsideCalcDoesNotBreakTheExpression() throws Exception {
        // Worst of the family: respaceCalcOperators reads the marker's own "/*" and "*/"
        // as division and multiplication and spaces them out, so valid CSS was emitted as
        // calc(100% / *___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_0___ * / - 10px), exit 0.
        String result = compress("a{width:calc(100% /* n */ - 10px)}b{color:#ff0000}");
        assertEquals("a{width:calc(100% - 10px)}b{color:red}", result, result);
    }

    @Test
    void aCommentInsideAPreservedSpanNeverLeavesItsMarkerInTheOutput() throws Exception {
        String dataUrl = compress("a{background:url('data:image/png;base64,AAA=' /* n */)}b{color:#ff0000}");
        assertEquals("a{background:url('data:image/png;base64,AAA=')}b{color:red}", dataUrl, dataUrl);

        String matrix = compress(
                "a{filter:progid:DXImageTransform.Microsoft.Matrix(M11=1 /* n */,M12=0)}b{color:#ff0000}");
        assertEquals("a{filter:progid:DXImageTransform.Microsoft.Matrix(M11=1 ,M12=0)}b{color:red}", matrix, matrix);

        for (String result : new String[] { dataUrl, matrix }) {
            assertTrue(result.indexOf("___YUICSSMIN") < 0, "internal scaffolding was emitted: " + result);
        }
    }

    @Test
    void aPreservedCommentInsideAPreservedSpanIsStillKept() throws Exception {
        // The marker is settled with the same rule the "kill the comment" loop uses, so
        // a "!" comment survives rather than being dropped along with the ordinary ones.
        String result = compress(
                "a{filter:progid:DXImageTransform.Microsoft.Matrix(M11=1 /*! keep */,M12=0)}b{color:#ff0000}");
        assertEquals("a{filter:progid:DXImageTransform.Microsoft.Matrix(M11=1 /*! keep */,M12=0)}b{color:red}",
                result, result);
    }

    @Test
    void aQuoteInsideAPreservedCommentDoesNotPutALinebreakInsideAString() throws Exception {
        // The linebreak pass tracked string state but not comments, so the '"' in the
        // banner opened a phantom string that the real string's opening quote closed.
        // The tracker then believed it was outside a string while inside one, and broke
        // the line at a "}" in the string's content - a parse error in CSS, exit 0.
        String source = "/*! say \"hi */a{content:\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa}bbbbbbbbbbbbbbbbbbbb\"}"
                + "c{color:#ff0000}";
        String result = compress(source, 20);
        assertEquals("/*! say \"hi */a{content:\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa}bbbbbbbbbbbbbbbbbbbb\"}\n"
                + "c{color:red}", result, result);

        // Same file without the quote: the break lands in the same place either way.
        String control = compress("/*! say hi */a{content:\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa}bbbbbbbbbbbbbbbbbbbb\"}"
                + "c{color:#ff0000}", 20);
        assertEquals(result.replace("say \"hi", "say hi"), control, control);
    }

    // ------------------------------------------------------------------
    // KNOWN DEFECTS, deferred to Release 2 by ruling R37. The two tests below pin
    // WRONG output, not correct output. They exist because the two defects are one
    // problem seen from two ends, and fixing either end alone relocates the
    // corruption instead of removing it:
    //
    //   A-3  preserveToken's regexes match "calc(" and "progid:...Matrix(" without
    //        knowing they are inside a string, and the captured span's placeholder
    //        is then never resolved - the restoration loop reaches index 0 before
    //        the enclosing string, which holds a higher index, is put back.
    //
    //   A-4  respaceCalcOperators runs after token restoration, so by then no
    //        string, comment or URL is protected any more.
    //
    // The trap: A-3 has a tempting one-line fix - resolve the nested reference in
    // the string-preserving pass, which the existing resolvePreservedTokenReferences
    // helper already does elsewhere. Land it alone and the string's real content
    // "calc(1px + 2px)" is restored, whereupon A-4 respaces it to
    // "calc(1px  +  2px)". The visible scaffolding becomes an invisible rewrite of
    // the author's text, which is worse, because it looks plausible.
    //
    // So: if one of these tests starts failing, the other end has to move in the
    // same commit. Both are pre-existing - byte-identical at the release base
    // 070bdd7 - and are recorded, not accepted as correct.
    // ------------------------------------------------------------------

    @Test
    void calcInsideAStringIsReplacedByScaffolding_knownDefectR37() throws Exception {
        // Valid CSS in, internal scaffolding out, exit code 0. Read A-4's test below
        // before changing any of these expectations.
        assertEquals("a{content:\"calc(___YUICSSMIN_PRESERVED_TOKEN_0___)\"}",
                compress("a{content:\"calc(1px + 2px)\"}"));
        assertEquals("a[data-x=\"calc(___YUICSSMIN_PRESERVED_TOKEN_0___)\"]{color:red}",
                compress("a[data-x=\"calc(1px + 2px)\"]{color:#ff0000}"));
        assertEquals("a{font-family:\"calc(___YUICSSMIN_PRESERVED_TOKEN_0___)\",serif}",
                compress("a{font-family:\"calc(x)\",serif}"));

        // Not only calc(): the progid rule has the same shape.
        assertEquals("a{content:\"progid:DXImageTransform.Microsoft.Matrix(___YUICSSMIN_PRESERVED_TOKEN_0___)\"}"
                + "b{color:red}",
                compress("a{content:\"progid:DXImageTransform.Microsoft.Matrix(M11=1)\"}b{color:#ff0000}"));
    }

    @Test
    void calcRespacingIgnoresStringAndCommentBoundaries_knownDefectR37() throws Exception {
        // Inside a preserved comment: harmless in a browser, since it is a comment,
        // but it is the same blindness and the cheapest demonstration of it.
        assertEquals("/*! calc(1px + 2px) */a{color:red}", compress("/*! calc(1px+2px) */a{color:#ff0000}"));

        // Inside a URL path. This input is a bad-url per CSS Syntax L3 4.3.14 - "(" is
        // not allowed in a url-token - so it is already invalid CSS; the quoted form,
        // which IS valid, is intercepted by A-3 above before it can reach here. That
        // is the entanglement: A-3 currently hides A-4's only valid-CSS string case.
        assertEquals("a{background:url(/x/calc(1px + 2px)/y.png)}b{color:red}",
                compress("a{background:url(/x/calc(1px+2px)/y.png)}b{color:#ff0000}"));

        // Control: the pass is doing its actual job, and must keep doing it.
        assertEquals("a{width:calc(100% - 10px)}b{color:red}", compress("a{width:calc(100%-10px)}b{color:#ff0000}"));
    }
}
