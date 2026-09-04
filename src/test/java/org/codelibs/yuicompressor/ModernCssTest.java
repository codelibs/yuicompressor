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

    // The line-break pass asks the same question collectComments asks - what region is
    // this offset in - and now answers it with the same three primitives. Each test
    // below is one region that a newline must not be inserted into.

    @Test
    void aCommentOpenerInsideAnUnquotedUrlDoesNotMoveTheLinebreak() throws Exception {
        // "/*" inside an unquoted url-token is ordinary URL content, not a comment -
        // collectComments has always had that right. The line-break pass did not, so it
        // ran to the next "*/" anywhere in the file, which is in the NEXT rule's string,
        // and put the newline inside the rule after that one. Valid CSS in, a raw
        // newline inside a string literal out, exit 0, so that declaration is dropped.
        assertEquals("a{background:url(/x/*p.png)}\nb{content:\"*/z\"}\n"
                + "c{content:\"aaaaaaaaaa}bbbbbbbbbb\"}\nd{color:red}",
                compress("a{background:url(/x/*p.png)}\nb{content:\"*/z\"}\n"
                        + "c{content:\"aaaaaaaaaa}bbbbbbbbbb\"}\nd{color:#ff0000}", 10));

        // Same shape through a preserved data: URL. Note it has to be the UNQUOTED
        // form: a quoted one is protected by the string region regardless.
        assertEquals("a{background:url(data:image/svg+xml,/*p)}\nb{content:\"*/z\"}\n"
                + "c{content:\"aaaaaaaaaa}bbbbbbbbbb\"}\nd{color:red}",
                compress("a{background:url(data:image/svg+xml,/*p)}\nb{content:\"*/z\"}\n"
                        + "c{content:\"aaaaaaaaaa}bbbbbbbbbb\"}\nd{color:#ff0000}", 10));
    }

    @Test
    void aBraceInsideAnUnquotedUrlIsNotARuleBoundary() throws Exception {
        // "}" is a perfectly legal character in a url-token (CSS Syntax L3 4.3.6 stops
        // only at ")", whitespace, quotes and "("), so this is valid CSS. The pass used
        // to treat it as the end of a rule and insert the newline inside the URL, which
        // makes it a bad-url-token - whitespace is exactly what a url-token cannot
        // contain - so the declaration was dropped.
        assertEquals("a{background:url(/x/}p.png)}\nbbbb{color:red}\ncccc{margin:0}\ndddd{padding:0}",
                compress("a{background:url(/x/}p.png)}\nbbbb{color:#ff0000}\ncccc{margin:0px}\n"
                        + "dddd{padding:0px}", 10));

        // The quoted form was already safe, and must stay that way.
        assertEquals("a{background:url(\"/x/}p.png\")}\nbbbb{color:red}\ncccc{margin:0}\ndddd{padding:0}",
                compress("a{background:url(\"/x/}p.png\")}\nbbbb{color:#ff0000}\ncccc{margin:0px}\n"
                        + "dddd{padding:0px}", 10));
    }

    @Test
    void breakingStillHappensWhereItShould() throws Exception {
        // Controls, so that a future "fix" cannot satisfy the tests above by simply
        // refusing to break. A "}" inside a comment or a string is not a boundary; one
        // ending a real rule is.
        assertEquals("aaaa{color:red}\nbbbb{margin:0}\ncccc{padding:0}",
                compress("aaaa{color:#ff0000}bbbb{margin:0px}cccc{padding:0px}", 10));
        assertEquals("aaaa{color:red}\nbbbb{margin:0}\ncccc{padding:0}",
                compress("aaaa{color:#ff0000}bbbb{margin:0px}cccc{padding:0px}", 0));
        assertEquals("/*! a } brace */aaaa{color:red}\nbbbb{color:red}",
                compress("/*! a } brace */aaaa{color:#ff0000}\nbbbb{color:#ff0000}", 10));
        assertEquals("aaaa{content:\"xxxxxxxxxx}yyyyyyyyyy\"}\nbbbb{color:red}",
                compress("aaaa{content:\"xxxxxxxxxx}yyyyyyyyyy\"}\nbbbb{color:#ff0000}", 10));
    }

    // data: URL white space (R38). Only a base64 payload may lose the white space
    // inside its quoted string; every other payload is literal data.

    @Test
    void whitespaceInsideANonBase64DataUrlIsSignificant() throws Exception {
        // The pin: SVG's viewBox grammar is four numbers separated by white space or
        // commas, so joining them into "002424" is one invalid value rather than four.
        // No browser is needed to adjudicate that, which is what makes it a good pin.
        assertEquals("a{background:url(\"data:image/svg+xml,<svg viewBox='0 0 24 24'/>\")}b{color:red}",
                compress("a{background:url(\"data:image/svg+xml,<svg viewBox='0 0 24 24'/>\")}b{color:#ff0000}"));

        // A text node's rendered content, for the same reason.
        assertEquals("a{background:url(\"data:image/svg+xml,<svg><text>hello world</text></svg>\")}",
                compress("a{background:url(\"data:image/svg+xml,<svg><text>hello world</text></svg>\")}"));
    }

    @Test
    void whitespaceInsideABase64DataUrlIsStillJoined() throws Exception {
        // Load-bearing, and pinned by the dataurl-base64-linebreakindata golden: a
        // base64 payload's white space is insignificant per RFC 2397, so joining it
        // is a real convenience and must survive.
        assertEquals("a{background:url(\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAE=\")}b{color:red}",
                compress("a{background:url(\"data:image/png;base64,iVBORw0KGgo\n   AAAANSUhEUg\n"
                        + "   AAAAE=\")}b{color:#ff0000}"));

        // ";base64" is the last thing before the comma, after any media-type
        // parameter, and RFC 2045 tokens are case-insensitive.
        assertEquals("a{background:url(\"data:text/plain;charset=UTF-8;base64,aGVsbG8gd29ybGQ=\")}b{color:red}",
                compress("a{background:url(\"data:text/plain;charset=UTF-8;base64,aGVsbG8g\n"
                        + " d29ybGQ=\")}b{color:#ff0000}"));
        assertEquals("a{background:url(\"data:image/png;BASE64,iVBORw0KGgo=\")}b{color:red}",
                compress("a{background:url(\"data:image/png;BASE64,iVBOR\n w0KGgo=\")}b{color:#ff0000}"));

        // ";base64" inside the DATA is not the header, so this payload stays literal.
        assertEquals("a{background:url(\"data:text/plain,a b;base64,c d\")}e{color:red}",
                compress("a{background:url(\"data:text/plain,a b;base64,c d\")}e{color:#ff0000}"));
    }

    @Test
    void whitespaceOutsideAQuotedDataUrlIsStillRemoved() throws Exception {
        // Two goldens pin this on NON-base64 URLs - dataurl-nonbase64-noquotes has
        // "url( data:...)" and dataurl-nonbase64-doublequotes puts the whole quoted
        // string on its own line - so the removal cannot be conditional on the payload.
        assertEquals("a{background:url(\"data:image/png,%89PNG%0D%0A\")}b{color:red}",
                compress("a{background:url(\n   \"data:image/png,%89PNG%0D%0A\"\n )}b{color:#ff0000}"));
        assertEquals("a{background:url(data:image/png,%89PNG%0D%0A)}b{color:red}",
                compress("a{background:url( data:image/png,%89PNG%0D%0A)}b{color:#ff0000}"));
    }

    @Test
    void dataUrlWhitespaceHandlingDoesNotReachOtherUrls() throws Exception {
        // Percent-encoded SVG - the machine-generated style - was never affected, which
        // is how the defect survived; and only data: URLs are preserved by that rule at
        // all, so an ordinary quoted URL keeps its space either way.
        assertEquals("a{background:url(\"data:image/svg+xml,<svg%20viewBox=%270%200%2024%2024%27/>\")}",
                compress("a{background:url(\"data:image/svg+xml,<svg%20viewBox=%270%200%2024%2024%27/>\")}"));
        assertEquals("a{background:url(\"/img/my file.png\")}b{color:red}",
                compress("a{background:url(\"/img/my file.png\")}b{color:#ff0000}"));
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

    @Test
    void commentsAfterAMalformedTokenAreNotCollected_acceptedRegressionR33() throws Exception {
        // ACCEPTED REGRESSION, not pre-existing. The structural scanner stops
        // understanding the document at an unterminated string or an unclosed url(,
        // and simply stops collecting from there, so later comments are emitted
        // instead of stripped. The old context-free scan did strip them - measured at
        // the release base 070bdd7, each of the four below loses its "/* n */" - but
        // it stripped them by not knowing where it was, which is the same blindness
        // that truncated whole stylesheets. Leaking a comment on malformed input is
        // the price, and it is a leak: nothing is deleted or rewritten.
        //
        // Every input here is invalid CSS. If one of these starts stripping again,
        // check what else changed with it.

        // Unclosed url(.
        assertEquals("a{background:url(x}/* n */b{color:red}",
                compress("a{background:url(x}/* n */b{color:#ff0000}"));

        // Unterminated string (R33 as filed).
        assertEquals("a{content:\"oops}b{color:red}/* n */c{margin:0}",
                compress("a{content:\"oops}b{color:#ff0000}/* n */c{margin:0px}"));

        // Same, closed by a newline. Per CSS Syntax L3 4.3.5 a newline ends the string
        // as a bad-string, so this one is "less malformed" than the case above, but
        // skipString runs past it and the outcome is identical.
        assertEquals("a{content:\"oops}b{color:red}/* n */c{margin:0}",
                compress("a{content:\"oops\n}\nb{color:#ff0000}\n/* n */\nc{margin:0px}"));

        // Stray quote inside an unquoted url(), which is a bad-url per 4.3.14. The
        // scanner steps over the quoted span deliberately - see skipUrlToken - because
        // the alternative deletes URL bytes.
        assertEquals("a{background:url(/x/y'.png)}/* n */b{color:red}",
                compress("a{background:url(/x/y'.png)}/* n */b{color:#ff0000}"));
    }

    @Test
    void anUnquotedUrlSpellingNameValueIsRewritten_knownDefectR32() throws Exception {
        // PRE-EXISTING, byte-identical at the release base 070bdd7, and deferred by
        // ruling R32. Unquoted URLs are only preserved when they are data:, so one
        // whose contents happen to spell a declaration reaches the value optimisers
        // and has its value rewritten - "0px" becomes "0" inside the URL.
        assertEquals("a{background:url(/x/*!k*/--y:0)}b{color:red}",
                compress("a{background:url(/x/*!k*/--y:0px)}b{color:#ff0000}"));

        // Why it was deferred rather than fixed: realistic URLs do not reach it.
        assertEquals("a{background:url(/img/0px-spacer.png)}b{color:red}",
                compress("a{background:url(/img/0px-spacer.png)}b{color:#ff0000}"));
    }

    // ------------------------------------------------------------------
    // KNOWN DEFECT, deferred to Release 2 by ruling R41. Unlike everything above it,
    // this one DESTROYS DATA rather than leaking, and it is deferred on reachability
    // and on the risk of a further behaviour change, not because the damage is small.
    // Pre-existing: byte-identical at the release base 070bdd7.
    //
    // Every region scan in this file decides that a string starts wherever it sees a
    // quote, without asking whether that quote is itself escaped - and "\"" is a valid
    // identifier escape (CSS Syntax L3 4.3.7). The phantom region then ends at the
    // OPENING quote of the next real string, leaving the scan running inside it.
    //
    // The trap for whoever fixes this: it is not one predicate in one place. Three
    // scans share the blindness - collectComments, insertLineBreaks, and the
    // string-preserving regex in compress - and a backslash PAIR before a quote is not
    // an escape, so the check has to count backslashes rather than look at one
    // character. The last two assertions in each test below pin exactly that.
    // ------------------------------------------------------------------

    @Test
    void anEscapedQuoteInASelectorStartsAPhantomString_knownDefectR41() throws Exception {
        // A. The line-break pass resumes inside c's string and breaks there. A raw
        //    newline in a CSS string is a parse error, so c is dropped.
        assertEquals("a\\\"b{color:red}\nc{content:\"XXXXXXXXXX}\nYYYYYYYYYY\"}",
                compress("a\\\"b{color:red}\nc{content:\"XXXXXXXXXX}YYYYYYYYYY\"}", 10));

        // B. Comment collection resumes inside c's string and deletes a comment-looking
        //    span that is really string content. The author's text is gone.
        assertEquals("a\\\"b{color:red}\nc{content:\"keep text\"}",
                compress("a\\\"b{color:red}\nc{content:\"keep /* this */ text\"}"));

        // Control: without the escaped quote both are correct, so the quote is the
        // trigger rather than anything about c.
        assertEquals("ab{color:red}c{content:\"keep /* this */ text\"}",
                compress("ab{color:red}\nc{content:\"keep /* this */ text\"}"));

        // Control: a backslash PAIR is not an escaped quote - there the quote really
        // does open a string - so "previous character is a backslash" is the wrong fix.
        assertEquals("a\\\\\"b\"{color:red}c{content:\"keep /* this */ text\"}",
                compress("a\\\\\"b\"{color:red}\nc{content:\"keep /* this */ text\"}"));
    }

    @Test
    void aTailwindArbitraryValueSelectorReachesTheSameBlindness_knownDefectR41() throws Exception {
        // The trigger is NOT confined to contrived selectors. Tailwind's arbitrary
        // value syntax - content-['x'] and the like - emits an escaped quote in the
        // generated selector, so this shape occurs in real, machine-generated CSS.
        //
        // What lands there is a missed optimisation rather than destruction: the
        // phantom string runs from the selector's escaped quote to the opening quote
        // of --tw-content's value, and that whole span is preserved verbatim, so its
        // whitespace is never collapsed. Note "::before { --tw-content: " keeps its
        // spaces below while the control does not. The destroying variants above need
        // a comment or a "}" inside a later real string, which this shape lacks - the
        // reachable trigger and the reachable harm are not the same thing, and that
        // distinction is what R41 turns on.
        assertEquals(".before\\:content-\\[\\'x\\'\\]::before { --tw-content: 'x';content:var(--tw-content)}"
                + ".b::after{content:\"0px and #ff0000\"}.mt-4{margin-top:1rem}",
                compress(".before\\:content-\\[\\'x\\'\\]::before { --tw-content: 'x'; content: var(--tw-content) }\n"
                        + ".b::after { content: \"0px and #ff0000\" }\n.mt-4 { margin-top: 1rem }"));

        // Control: the same rule with an ordinary selector minifies fully.
        assertEquals(".plain::before{--tw-content:'x';content:var(--tw-content)}"
                + ".b::after{content:\"0px and #ff0000\"}.mt-4{margin-top:1rem}",
                compress(".plain::before { --tw-content: 'x'; content: var(--tw-content) }\n"
                        + ".b::after { content: \"0px and #ff0000\" }\n.mt-4 { margin-top: 1rem }"));

        // One consequence IS a regression from this release rather than pre-existing:
        // a following "/*!" banner is no longer collected as a comment, so its text is
        // exposed to the minifier and loses the space after "!". At 070bdd7 the
        // context-free scan collected it regardless of the phantom string, and the
        // banner survived intact. Cosmetic, inside a comment, but it is author text.
        assertEquals(".before\\:content-\\[\\'x\\'\\]::before { --tw-content: 'x';content:var(--tw-content)}"
                + "/*!(c) Acme 2026 */.mt-4{margin-top:1rem}",
                compress(".before\\:content-\\[\\'x\\'\\]::before { --tw-content: 'x'; content: var(--tw-content) }\n"
                        + "/*! (c) Acme 2026 */\n.mt-4 { margin-top: 1rem }"));
    }
}
