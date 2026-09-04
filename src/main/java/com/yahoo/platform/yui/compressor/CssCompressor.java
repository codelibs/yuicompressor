/*
 * YUI Compressor
 * http://developer.yahoo.com/yui/compressor/
 * Author: Julien Lecomte -  http://www.julienlecomte.net/
 * Author: Isaac Schlueter - http://foohack.com/
 * Author: Stoyan Stefanov - http://phpied.com/
 * Contributor: Dan Beam - http://danbeam.org/
 * Copyright (c) 2013 Yahoo! Inc.  All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */
package com.yahoo.platform.yui.compressor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;

public class CssCompressor {

    private StringBuffer srcsb = new StringBuffer();

    public CssCompressor(Reader in) throws IOException {
        // Read the stream...
        int c;
        while ((c = in.read()) != -1) {
            srcsb.append((char) c);
        }
    }

    /**
     * @param css - full css string
     * @param preservedToken - token to preserve
     * @param tokenRegex - regex to find token
     * @param removeWhiteSpace - remove any white space in the token
     * @param preservedTokens - array of token values
     * @return
     */
    protected String preserveToken(String css, String preservedToken,
            String tokenRegex, boolean removeWhiteSpace, ArrayList preservedTokens) {

        int maxIndex = css.length() - 1;
        int appendIndex = 0;

        StringBuffer sb = new StringBuffer();

        Pattern p = Pattern.compile(tokenRegex);
        Matcher m = p.matcher(css);

        while (m.find()) {
            int startIndex = m.start() + (preservedToken.length() + 1);
            String terminator = m.group(1);

            // skip this, if CSS was already copied to "sb" upto this position
            if (m.start() < appendIndex) {
                continue;
            }

            if (terminator.length() == 0) {
                terminator = ")";
            }

            boolean foundTerminator = false;

            int endIndex = m.end() - 1;
            while(foundTerminator == false && endIndex+1 <= maxIndex) {
                endIndex = css.indexOf(terminator, endIndex+1);

                if (endIndex <= 0) {
                    break;
                } else if ((endIndex > 0) && (css.charAt(endIndex-1) != '\\')) {
                    foundTerminator = true;
                    if (!")".equals(terminator)) {
                        endIndex = css.indexOf(")", endIndex);
                    }
                }
            }

            // Enough searching, start moving stuff over to the buffer
            sb.append(css.substring(appendIndex, m.start()));

            if (foundTerminator) {
                String token = css.substring(startIndex, endIndex);
                if(removeWhiteSpace)
                    token = token.replaceAll("\\s+", "");
                preservedTokens.add(token);

                String preserver = preservedToken + "(___YUICSSMIN_PRESERVED_TOKEN_" + (preservedTokens.size() - 1) + "___)";
                sb.append(preserver);

                appendIndex = endIndex + 1;
            } else {
                // No end terminator found, re-add the whole match. Should we throw/warn here?
                sb.append(css.substring(m.start(), m.end()));
                appendIndex = m.end();
            }
        }

        sb.append(css.substring(appendIndex));

        return sb.toString();
    }

    /**
     * Replaces every @property at-rule block with a preserved token. A descriptor such
     * as initial-value must be kept verbatim, just like a custom property value, so the
     * simplest and safest approach is to preserve the whole block as one opaque unit.
     * This must run after comment and string preservation (see the call site), so that
     * "@property" cannot be matched inside a comment and a brace inside a descriptor
     * string cannot be mistaken for the block's own closing brace. Because of that
     * ordering, the captured block can itself contain an already-preserved-token
     * placeholder (e.g. its syntax descriptor's quoted value); resolvePreservedTokenReferences
     * flattens that out before the block is stored, the same way preserveCustomPropertyValues
     * does for a captured declaration value.
     */
    private String preservePropertyAtRuleBlocks(String css, ArrayList preservedTokens) {
        StringBuffer sb = new StringBuffer();
        int i = 0;
        int len = css.length();
        Pattern p = Pattern.compile("(?i)@property\\b");
        Matcher m = p.matcher(css);
        while (i < len) {
            if (!m.find(i)) {
                sb.append(css, i, len);
                break;
            }
            int start = m.start();
            if (!startsAtBoundary(css, start, AT_RULE_BOUNDARIES)) {
                // The text "@property" inside a value - url(/img/@property.png)
                // is the case that was reported - is not an at-rule. Treating it
                // as one preserved everything from there to the next balanced
                // "}" verbatim, which silently left the FOLLOWING rule entirely
                // unminified. Copy it through and keep looking.
                sb.append(css, i, m.end());
                i = m.end();
                continue;
            }
            int brace = css.indexOf('{', m.end());
            if (brace < 0) {
                sb.append(css, i, len);
                break;
            }
            int depth = 0;
            int end = brace;
            while (end < len) {
                char c = css.charAt(end);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
                end++;
            }
            if (end >= len) {
                // No matching closing brace found; leave the rest of the CSS as-is.
                sb.append(css, i, len);
                break;
            }
            sb.append(css, i, start);
            String block = resolvePreservedTokenReferences(css.substring(start, end + 1), preservedTokens);
            preservedTokens.add(block);
            sb.append("___YUICSSMIN_PRESERVED_TOKEN_").append(preservedTokens.size() - 1).append("___");
            i = end + 1;
        }
        return sb.toString();
    }

    /**
     * Replaces the value of every custom property declaration with a preserved
     * token so that the later value transformations cannot rewrite it. A custom
     * property value is an arbitrary token stream and must survive verbatim.
     */
    private String preserveCustomPropertyValues(String css, ArrayList preservedTokens) {
        StringBuffer sb = new StringBuffer();
        int i = 0;
        int len = css.length();
        while (i < len) {
            int start = css.indexOf("--", i);
            if (start < 0) {
                sb.append(css, i, len);
                break;
            }
            // A declaration starts after '{' or ';' (or a preserved-token
            // placeholder standing in for a comment that precedes it), which
            // distinguishes a custom property from a '--' appearing inside a
            // value such as calc(a --b).
            if (!startsAtBoundary(css, start, DECLARATION_BOUNDARIES)) {
                sb.append(css, i, start + 2);
                i = start + 2;
                continue;
            }
            int colon = css.indexOf(':', start);
            if (colon < 0) {
                sb.append(css, i, len);
                break;
            }
            int depth = 0;
            int end = colon + 1;
            while (end < len) {
                char c = css.charAt(end);
                if (c == '(' || c == '[' || c == '{') {
                    depth++;
                } else if (c == ')' || c == ']') {
                    depth--;
                } else if (c == '}') {
                    if (depth == 0) {
                        break;
                    }
                    depth--;
                } else if (c == ';' && depth == 0) {
                    break;
                }
                end++;
            }
            // A value such as --content: "a;b"; already had its string content pulled out
            // by the string-preservation step above, so what we capture here is
            // ...___YUICSSMIN_PRESERVED_TOKEN_0___..., not the raw string. Resolve any
            // such reference now, against the already-preserved tokens, and store the
            // fully expanded value. Without this, the token we add below would embed a
            // reference to an earlier (lower) index, and the single forward pass that
            // restores preserved tokens at the end of compress() would never revisit
            // that earlier index once it's inserted, leaving the placeholder unresolved
            // in the final output.
            String value = css.substring(colon + 1, end).trim();
            value = resolvePreservedTokenReferences(value, preservedTokens);
            preservedTokens.add(value);
            sb.append(css, i, colon + 1);
            sb.append("___YUICSSMIN_PRESERVED_TOKEN_").append(preservedTokens.size() - 1).append("___");
            i = end;
        }
        return sb.toString();
    }

    private static final String PRESERVED_TOKEN_PREFIX = "___YUICSSMIN_PRESERVED_TOKEN_";

    private static final String PRESERVE_CANDIDATE_COMMENT_PREFIX = "___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_";

    private static final Pattern PRESERVED_TOKEN_REFERENCE = Pattern.compile("___YUICSSMIN_PRESERVED_TOKEN_(\\d+)___");

    /** Characters an at-rule can legitimately follow. */
    private static final String AT_RULE_BOUNDARIES = "{};";

    /** Characters a declaration can legitimately follow. */
    private static final String DECLARATION_BOUNDARIES = "{;";

    /**
     * Whether the construct starting at {@code start} begins somewhere it
     * legitimately can: at the start of the stylesheet, immediately after one
     * of {@code boundaries}, or immediately after a preserved-token
     * placeholder. Whitespace in between is skipped.
     *
     * <p>Matching one of these shapes context-free - anywhere its literal text
     * happens to appear - is a defect class this file has hit more than once.
     * The comment matcher used to fire inside a string; "@property" fires
     * inside {@code url(/img/@property.png)} and swallows the following rule
     * with it; the at-directive lowercasing pass rewrites
     * {@code url(/img/@MEDIA.png)} to {@code @media} even though URL paths are
     * case-sensitive. Requiring a real boundary closes all of them the same
     * way, which is why this is one shared helper rather than a check repeated
     * per matcher.
     *
     * <p>A preserved COMMENT also forms a boundary, and that case is not
     * optional: by the time these passes run, a leading comment is already a
     * placeholder, so a declaration or at-rule written directly after a
     * preserved "/*!" banner is preceded by that placeholder rather than by
     * "{" or "}". Omitting it is exactly how custom property values stopped
     * being preserved when a preserved token preceded them.
     *
     * <p>A preserved comment is the ONLY placeholder form that counts here.
     * Enumerated from the preservation passes that run before these scans,
     * three textual forms exist at this point, and only the first can
     * legitimately precede a declaration or at-rule:
     *
     * <ul>
     * <li>"/*" + placeholder + "*" + "/" - a preserved "/*!" banner, the
     * Mac/IE5 backslash hack, or the IE7 "&gt;/**" + "/" hack. Real: a banner
     * can sit between "{" and a declaration.
     * <li>a quoted placeholder - a preserved string literal. NOT a boundary: a
     * string abutting a declaration or at-rule ("a{content:\"x\"--y:1}") is not
     * valid CSS, and a string in a value is followed by ";" or "}", which is
     * already a boundary. Accepting it reintroduces the very defect this
     * method fixes - "url(/x/\"y\"@property.png)" is then read as an at-rule
     * and the FOLLOWING rule is left unminified.
     * <li>a bare placeholder - the "\9" hack, or the inside of a preserved
     * "url(...)". NOT a boundary either: "\9" ends a declaration value so a
     * ";" or "}" follows it, and a bare placeholder inside "url(...)" is
     * followed by ")".
     * </ul>
     *
     * <p>One accepted cost of restricting to comments: an at-rule written
     * directly after a preserved {@code @property} block is preceded by that
     * block's bare placeholder, so it is not recognised and its name is not
     * lowercased - {@code @property --x{...}@MEDIA screen{...}} keeps
     * {@code @MEDIA}. At-rule names are ASCII case-insensitive, so this is a
     * missed optimisation on a rare adjacency, and the alternative (accepting
     * bare placeholders) is what reopened the corruption above. Recorded rather
     * than fixed.
     *
     * <p>An ordinary (non-preserved) TERMINATED comment does not reach this
     * test: it is deleted whole, "/*" and "*" + "/" included, by the "kill the
     * comment" pass, so "{" ends up directly adjacent to what follows it. That
     * is why routine CSS such as ":root{/* note *" + "/--pad:0px}" was never
     * affected by the defect this method fixes, and must not start being
     * affected by the fix.
     *
     * <p>That is a statement about terminated comments only, and it is worth
     * not restating as a general invariant: an UNTERMINATED "/*" used to be
     * collected as a comment running to end-of-input, whose placeholder the
     * kill pass could not match, so it did reach here - and truncated the
     * stylesheet on the way. {@link #collectComments} now rejects that input
     * rather than guessing at it, which is what makes the sentence above true
     * as far as it goes. Neither statement is a guarantee that some third
     * shape cannot arrive; the predicate is defensive about what it accepts
     * for that reason.
     */
    private static boolean startsAtBoundary(String css, int start, String boundaries) {
        int before = start - 1;
        while (before >= 0 && Character.isWhitespace(css.charAt(before))) {
            before--;
        }
        if (before < 0) {
            return true;
        }
        return boundaries.indexOf(css.charAt(before)) >= 0 || endsWithPreservedComment(css, before + 1);
    }

    /**
     * Whether a preserved COMMENT ends exactly at {@code endExclusive} - the
     * full text "/*" + "___YUICSSMIN_PRESERVED_TOKEN_n___" + "*" + "/".
     *
     * <p>BOTH delimiters are required, which is what keeps this to the one
     * placeholder form that can legitimately precede a declaration or at-rule;
     * see {@link #startsAtBoundary} for the enumeration of the three forms and
     * why the other two must be rejected. Preservation leaves the delimiters in
     * place because the comment pass replaces only the inner candidate marker.
     *
     * <p>Checked structurally (delimiter, trailing "___", at least one index
     * digit, the prefix, opening delimiter) rather than with a backwards regex,
     * so a stylesheet that merely contains the prefix as literal text cannot be
     * mistaken for one.
     */
    private static boolean endsWithPreservedComment(String css, int endExclusive) {
        int i = endExclusive;
        if (i < 2 || !css.startsWith("*/", i - 2)) {
            return false;
        }
        i -= 2;
        if (i < 3 || !css.startsWith("___", i - 3)) {
            return false;
        }
        i -= 3;
        int digitsEnd = i;
        while (i > 0 && Character.isDigit(css.charAt(i - 1))) {
            i--;
        }
        if (i == digitsEnd) {
            return false;
        }
        int prefixStart = i - PRESERVED_TOKEN_PREFIX.length();
        return prefixStart >= 2
                && css.startsWith(PRESERVED_TOKEN_PREFIX, prefixStart)
                && css.startsWith("/*", prefixStart - 2);
    }

    /**
     * Expands any placeholder already inserted by an earlier preservation step (a
     * preserved string, in particular) back to its real text. See the comment at the
     * call site in preserveCustomPropertyValues for why this is necessary.
     */
    private String resolvePreservedTokenReferences(String value, ArrayList preservedTokens) {
        if (value.indexOf("___YUICSSMIN_PRESERVED_TOKEN_") < 0) {
            return value;
        }
        Matcher m = PRESERVED_TOKEN_REFERENCE.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int index = Integer.parseInt(m.group(1));
            String replacement = index < preservedTokens.size() ? preservedTokens.get(index).toString() : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Replaces the body of every comment with a candidate marker, leaving the
     * "/*" and "*" + "/" delimiters in place.
     *
     * <p>This is the first pass over the stylesheet, so nothing has been
     * preserved yet and it has to understand CSS structure itself. It used to
     * be a bare {@code indexOf("/*")} loop with an {@code endIndex = totallen}
     * fallback, which produced two defects that are really one - the scan was
     * context-free and ran before string and URL handling:
     *
     * <ul>
     * <li>A comment-looking span inside a string or an unquoted {@code url()}
     * was collected as a comment. The resulting placeholder then sat in the
     * middle of a value while <em>looking</em> exactly like a leading banner
     * comment, which defeated {@link #startsAtBoundary} at all four of its
     * call sites - a placeholder's shape records how it was created, never
     * where it sits, so no amount of narrowing that predicate could have
     * fixed it. {@code url(/x/}{@code *!k*}{@code /@charset "y";.png)} invented
     * a stylesheet encoding out of a URL fragment and deleted the fragment.
     * <li>An UNTERMINATED "/*" replaced everything to end-of-input with a
     * marker that the later "kill the comment" pass could not match, because
     * that pass looks for the closing delimiter. The stylesheet was truncated,
     * the following rules were lost, internal scaffolding was emitted into
     * shippable CSS, and the exit code was 0. {@code a{content:"/*"}} - valid
     * CSS - was enough to trigger it.
     * </ul>
     *
     * <p>Both are fixed at the root by scanning structurally: strings and
     * {@code url()} tokens are stepped over, so their contents can never be
     * mistaken for a comment.
     *
     * <p>An unterminated comment outside a string or URL throws. Browsers
     * consume such a comment to end-of-input, so the stylesheet is already
     * broken for the author either way; reproducing that here would mean a
     * minifier silently discarding the rest of the file with a success exit
     * code, which is indistinguishable from the corruption this pass exists to
     * stop. Failing loudly on malformed input is the deliberate trade.
     */
    private String collectComments(String css, ArrayList comments) {
        StringBuilder out = new StringBuilder(css.length());
        int i = 0;
        int len = css.length();
        while (i < len) {
            char c = css.charAt(i);
            if (c == '"' || c == '\'') {
                int end = skipString(css, i);
                out.append(css, i, end);
                i = end;
                continue;
            }
            if (startsUrlToken(css, i)) {
                int end = skipUrlToken(css, i);
                out.append(css, i, end);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < len && css.charAt(i + 1) == '*') {
                int end = css.indexOf("*/", i + 2);
                if (end < 0) {
                    throw new IllegalArgumentException("unterminated CSS comment: \"/*\" at offset " + i
                            + " has no closing \"*/\". Refusing to compress, because treating it as a comment "
                            + "would silently discard the remaining " + (len - i) + " characters of the stylesheet.");
                }
                comments.add(css.substring(i + 2, end));
                out.append("/*").append(PRESERVE_CANDIDATE_COMMENT_PREFIX)
                        .append(comments.size() - 1).append("___").append("*/");
                i = end + 2;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Index just past the closing quote, or the end of input if unterminated. */
    private static int skipString(String css, int start) {
        char quote = css.charAt(start);
        int i = start + 1;
        while (i < css.length()) {
            char c = css.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            i++;
        }
        return css.length();
    }

    /**
     * Whether a url-token - the raw-content form - starts at {@code i}. Two
     * conditions, both from CSS Syntax Level 3 &sect;4.3.4 "consume an
     * ident-like token":
     *
     * <ul>
     * <li>The preceding character must not be an identifier character, so
     * {@code myurl(} is not mistaken for one.
     * <li>The {@code url(} must not be followed, after any amount of
     * whitespace, by a quote. {@code url("a.png")} is an ordinary
     * {@code <function-token>} whose contents are ordinary tokens - strings,
     * comments, and the closing {@code )} - so the main loop has to scan it
     * with the normal rules. Only the unquoted form is a url-token whose
     * content is consumed raw, and only that form may be handed to
     * {@link #skipUrlToken}.
     * </ul>
     *
     * <p>Getting the second condition wrong is not a missed optimisation: a
     * comment inside a quoted {@code url()} desynced the raw scan, and the
     * collector then deleted the tail of an unrelated comment, leaving an
     * unterminated {@code /*} in shippable CSS with exit code 0.
     */
    private static boolean startsUrlToken(String css, int i) {
        if (!css.regionMatches(true, i, "url(", 0, 4)) {
            return false;
        }
        if (i > 0) {
            char prev = css.charAt(i - 1);
            if (Character.isLetterOrDigit(prev) || prev == '-' || prev == '_') {
                return false;
            }
        }
        int j = i + 4;
        while (j < css.length() && isCssSpace(css.charAt(j))) {
            j++;
        }
        if (j >= css.length()) {
            return true;
        }
        char next = css.charAt(j);
        return next != '"' && next != '\'';
    }

    /**
     * Index just past the closing ")" of an unquoted url-token, or the end of
     * input if unterminated. Only ever reached where {@link #startsUrlToken}
     * says the raw form starts, so no comment is recognised inside it - which
     * is exactly what makes {@code url(/x/}{@code *!k*}{@code /a.png)} not a
     * comment.
     *
     * <p>The quote handling looks like a spec deviation and is deliberate.
     * Strictly, a quote inside this form is a parse error: the token becomes a
     * bad-url-token whose remnants are consumed to the first unescaped ")"
     * (CSS Syntax Level 3 &sect;4.3.14), so a spec-literal scan would ignore
     * quotes entirely. Doing that was measured, and it silently deletes URL
     * bytes: in
     * {@code url(data:image/svg+xml,<svg xmlns="a)b/}{@code *x*}{@code /c"/>)}
     * the spec-literal scan ends the token at the ")" inside the attribute,
     * the main loop then resumes <em>inside the URL</em>, and
     * {@code /}{@code *x*}{@code /} is collected and deleted as a comment -
     * defect A of {@code 9b56de5}, reintroduced. Stepping over the quoted span
     * instead cannot delete anything: its only failure mode is ending the
     * token late, which suppresses comment collection (a leak) rather than
     * causing it (corruption). On malformed input this errs toward emitting
     * too much, which is the safe direction.
     */
    private static int skipUrlToken(String css, int start) {
        int i = start + 4; // past "url("
        while (i < css.length()) {
            char c = css.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipString(css, i);
                continue;
            }
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == ')') {
                return i + 1;
            }
            i++;
        }
        return css.length();
    }

    /**
     * Settles candidate comment markers that ended up inside an already-preserved
     * span, applying the same rule the "kill the comment" loop applies to the rest
     * of the stylesheet: a comment whose body starts with "!" is kept, any other is
     * deleted.
     *
     * <p>{@link #preserveToken} captures its span verbatim and that span is put back
     * only at the very end of {@code compress}, after the loop that resolves those
     * markers has run. Anything the span swallowed is therefore invisible to that
     * loop and is emitted as-is. Measured before this existed, on valid CSS and with
     * exit code 0:
     *
     * <pre>
     * a{width:calc(100% /* n *&#47; - 10px)}
     *   -&gt; a{width:calc(100% / *___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_0___ * / - 10px)}
     * </pre>
     *
     * The marker is internal scaffolding, and inside {@code calc()} it is worse than
     * ugly: {@link #respaceCalcOperators} runs after restoration, reads the marker's
     * own "/*" and "*&#47;" as division and multiplication operators, and spaces them
     * out - so a valid declaration is emitted broken.
     *
     * <p>Only the three {@code preserveToken} calls have run when this is invoked, so
     * every entry is one of their spans.
     */
    private static void resolveCandidateComments(ArrayList preservedTokens, ArrayList comments) {
        for (int t = 0; t < preservedTokens.size(); t++) {
            String value = preservedTokens.get(t).toString();
            if (value.indexOf(PRESERVE_CANDIDATE_COMMENT_PREFIX) < 0) {
                continue;
            }
            for (int c = 0; c < comments.size(); c++) {
                String body = comments.get(c).toString();
                String marker = "/*" + PRESERVE_CANDIDATE_COMMENT_PREFIX + c + "___*/";
                value = value.replace(marker, body.startsWith("!") ? "/*" + body + "*/" : "");
            }
            preservedTokens.set(t, value);
        }
    }

    public void compress(Writer out, int linebreakpos)
            throws IOException {

        Pattern p;
        Matcher m;
        String css = srcsb.toString();

        int startIndex = 0;
        int i = 0;
        int max = 0;
        ArrayList preservedTokens = new ArrayList(0);
        ArrayList comments = new ArrayList(0);
        String token;
        String placeholder;

        StringBuffer sb;

        // collect all comment blocks...
        css = collectComments(css, comments);


        css = this.preserveToken(css, "url", "(?i)url\\(\\s*([\"']?)data\\:", true, preservedTokens);
        css = this.preserveToken(css, "calc",  "(?i)calc\\(\\s*([\"']?)", false, preservedTokens);
        css = this.preserveToken(css, "progid:DXImageTransform.Microsoft.Matrix",  "(?i)progid:DXImageTransform.Microsoft.Matrix\\s*([\"']?)", false, preservedTokens);

        // Each span captured just above is restored verbatim at the very end, so a
        // comment inside one never reaches the "kill the comment" loop below and its
        // candidate marker would be emitted into shippable CSS. Settle those markers
        // here, with the same rule that loop applies.
        resolveCandidateComments(preservedTokens, comments);

        // preserve strings so their content doesn't get accidentally minified
        sb = new StringBuffer();
        p = Pattern.compile("(\"([^\\\\\"]|\\\\.|\\\\)*\")|(\'([^\\\\\']|\\\\.|\\\\)*\')");
        m = p.matcher(css);
        while (m.find()) {
            token = m.group();
            char quote = token.charAt(0);
            token = token.substring(1, token.length() - 1);

            // maybe the string contains a comment-like substring?
            // one, maybe more? put'em back then
            if (token.indexOf("___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_") >= 0) {
                for (i = 0, max = comments.size(); i < max; i += 1) {
                    token = token.replace("___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_" + i + "___", comments.get(i).toString());
                }
            }

            // minify alpha opacity in filter strings
            token = token.replaceAll("(?i)progid:DXImageTransform.Microsoft.Alpha\\(Opacity=", "alpha(opacity=");

            preservedTokens.add(token);
            String preserver = quote + "___YUICSSMIN_PRESERVED_TOKEN_" + (preservedTokens.size() - 1) + "___" + quote;
            m.appendReplacement(sb, preserver);
        }
        m.appendTail(sb);
        css = sb.toString();


        // strings are safe, now wrestle the comments
        for (i = 0, max = comments.size(); i < max; i += 1) {

            token = comments.get(i).toString();
            placeholder = "___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_" + i + "___";

            // ! in the first position of the comment means preserve
            // so push to the preserved tokens while stripping the !
            if (token.startsWith("!")) {
                preservedTokens.add(token);
                css = css.replace(placeholder,  "___YUICSSMIN_PRESERVED_TOKEN_" + (preservedTokens.size() - 1) + "___");
                continue;
            }

            // \ in the last position looks like hack for Mac/IE5
            // shorten that to /*\*/ and the next one to /**/
            if (token.endsWith("\\")) {
                preservedTokens.add("\\");
                css = css.replace(placeholder,  "___YUICSSMIN_PRESERVED_TOKEN_" + (preservedTokens.size() - 1) + "___");
                i = i + 1; // attn: advancing the loop
                preservedTokens.add("");
                css = css.replace("___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_" + i + "___",  "___YUICSSMIN_PRESERVED_TOKEN_" + (preservedTokens.size() - 1) + "___");
                continue;
            }

            // keep empty comments after child selectors (IE7 hack)
            // e.g. html >/**/ body
            if (token.length() == 0) {
                startIndex = css.indexOf(placeholder);
                if (startIndex > 2) {
                    if (css.charAt(startIndex - 3) == '>') {
                        preservedTokens.add("");
                        css = css.replace(placeholder,  "___YUICSSMIN_PRESERVED_TOKEN_" + (preservedTokens.size() - 1) + "___");
                    }
                }
            }

            // in all other cases kill the comment
            css = css.replace("/*" + placeholder + "*/", "");
        }

        // preserve \9 IE hack
        final String backslash9 = "\\9"; 
        while (css.indexOf(backslash9) > -1) {
            preservedTokens.add(backslash9);
            css = css.replace(backslash9,  "___YUICSSMIN_PRESERVED_TOKEN_" + (preservedTokens.size() - 1) + "___");
     	}

        // Preserve @property at-rule blocks verbatim. A descriptor such as
        // initial-value is an arbitrary token stream, just like a custom property
        // value, and must survive unchanged. This runs after comment and string
        // preservation above, so "@property" inside a comment cannot match here (the
        // comment is already an opaque placeholder) and a brace inside a descriptor
        // string cannot be mistaken for the block's closing brace (the string is
        // already an opaque placeholder too). It runs before the custom-property scan
        // below so that scan never has to reason about "@property" blocks at all.
        css = this.preservePropertyAtRuleBlocks(css, preservedTokens);

        // Preserve custom property declaration values verbatim. A custom property's
        // value is an arbitrary token stream (spec-defined), not a value the color/
        // keyword optimisers below understand, so it must survive unchanged. This runs
        // after string/URL/calc preservation above so that a quote or semicolon inside
        // an already-preserved token cannot be mistaken for the end of the value.
        css = this.preserveCustomPropertyValues(css, preservedTokens);

        // Normalize all whitespace strings to single spaces. Easier to work with that way.
        css = css.replaceAll("\\s+", " ");

        // Remove the spaces before the things that should not have spaces before them.
        // But, be careful not to turn "p :link {...}" into "p:link{...}"
        // Swap out any pseudo-class colons with the token, and then swap back.
        sb = new StringBuffer();
        p = Pattern.compile("(^|\\})((^|([^\\{:])+):)+([^\\{]*\\{)");
        m = p.matcher(css);
        while (m.find()) {
            String s = m.group();
            s = s.replaceAll(":", "___YUICSSMIN_PSEUDOCLASSCOLON___");
            s = s.replaceAll( "\\\\", "\\\\\\\\" ).replaceAll( "\\$", "\\\\\\$" );
            m.appendReplacement(sb, s);
        }
        m.appendTail(sb);
        css = sb.toString();
        // Remove spaces before the things that should not have spaces before them.
        css = css.replaceAll("\\s+([!{};:>+\\)\\],])", "$1");
        // Restore spaces for !important
        css = css.replaceAll("!important", " !important");
        // bring back the colon
        css = css.replaceAll("___YUICSSMIN_PSEUDOCLASSCOLON___", ":");

        // retain space for special IE6 cases
        sb = new StringBuffer();
        p = Pattern.compile("(?i):first\\-(line|letter)(\\{|,)");
        m = p.matcher(css);
        while (m.find()) {
            m.appendReplacement(sb, ":first-" + m.group(1).toLowerCase() + " " + m.group(2));
        }
        m.appendTail(sb);
        css = sb.toString();

        // no space after the end of a preserved comment
        css = css.replaceAll("\\*/ ", "*/");

        // If there are multiple @charset directives, push them to the top of the file.
        // Guarded by the same boundary rule as the other at-rule matchers: without
        // it, the literal text "@charset \"y\";" inside an unpreserved url() is
        // hoisted out of the URL and to the top of the stylesheet.
        sb = new StringBuffer();
        p = Pattern.compile("(?i)^(.*)(@charset)( \"[^\"]*\";)");
        m = p.matcher(css);
        while (m.find()) {
            if (!startsAtBoundary(css, m.start(2), AT_RULE_BOUNDARIES)) {
                continue;
            }
            String s = m.group(1).replaceAll("\\\\", "\\\\\\\\").replaceAll("\\$", "\\\\\\$");
            m.appendReplacement(sb, m.group(2).toLowerCase() + m.group(3) + s);
        }
        m.appendTail(sb);
        css = sb.toString();

        // When all @charset are at the top, remove the second and after (as they are completely ignored).
        sb = new StringBuffer();
        p = Pattern.compile("(?i)^((\\s*)(@charset)( [^;]+;\\s*))+");
        m = p.matcher(css);
        while (m.find()) {
            m.appendReplacement(sb, m.group(2) + m.group(3).toLowerCase() + m.group(4));
        }
        m.appendTail(sb);
        css = sb.toString();

        // lowercase some popular @directives (@charset is done right above).
        // Only where one can actually start: this pattern is otherwise
        // context-free, and "url(/img/@MEDIA.png)" is a real URL path, which
        // servers treat as case-sensitive. Same defect class as @property below.
        sb = new StringBuffer();
        p = Pattern.compile("(?i)@(font-face|import|(?:-(?:atsc|khtml|moz|ms|o|wap|webkit)-)?keyframe|media|page|namespace|supports|container|layer|property|scope|starting-style)");
        m = p.matcher(css);
        while (m.find()) {
            if (!startsAtBoundary(css, m.start(), AT_RULE_BOUNDARIES)) {
                continue;
            }
            m.appendReplacement(sb, '@' + m.group(1).toLowerCase());
        }
        m.appendTail(sb);
        css = sb.toString();

        // lowercase some more common pseudo-elements
        sb = new StringBuffer();
        p = Pattern.compile("(?i):(active|after|before|checked|disabled|empty|enabled|first-(?:child|of-type)|focus|hover|last-(?:child|of-type)|link|only-(?:child|of-type)|root|:selection|target|visited)");
        m = p.matcher(css);
        while (m.find()) {
            m.appendReplacement(sb, ':' + m.group(1).toLowerCase());
        }
        m.appendTail(sb);
        css = sb.toString();

        // lowercase some more common functions
        sb = new StringBuffer();
        p = Pattern.compile("(?i):(lang|not|nth-child|nth-last-child|nth-last-of-type|nth-of-type|(?:-(?:moz|webkit)-)?any)\\(");
        m = p.matcher(css);
        while (m.find()) {
            m.appendReplacement(sb, ':' + m.group(1).toLowerCase() + '(');
        }
        m.appendTail(sb);
        css = sb.toString();

        // lower case some common function that can be values
        // NOTE: rgb() isn't useful as we replace with #hex later, as well as and() is already done for us right after this
        sb = new StringBuffer();
        p = Pattern.compile("(?i)([:,\\( ]\\s*)(attr|color-stop|from|rgba|to|url|(?:-(?:atsc|khtml|moz|ms|o|wap|webkit)-)?(?:calc|max|min|(?:repeating-)?(?:linear|radial)-gradient)|-webkit-gradient)");
        m = p.matcher(css);
        while (m.find()) {
            m.appendReplacement(sb, m.group(1) + m.group(2).toLowerCase());
        }
        m.appendTail(sb);
        css = sb.toString();

        // Normalize the casing of media/supports query keywords, to support stuff like
        // @media screen AND (-webkit-min-device-pixel-ratio:0){
        // $1 would echo the input's casing verbatim, so lowercase it explicitly.
        Pattern keywordPattern = Pattern.compile("(?i)\\b(and|or|not)(\\s*\\()");
        Matcher keywordMatcher = keywordPattern.matcher(css);
        StringBuffer keywordSb = new StringBuffer();
        while (keywordMatcher.find()) {
            // Normalise the keyword's case only. Whitespace is deliberately left
            // exactly as it was: inserting a space here would turn the ident in
            // ":not(...)" into a separate token and break the selector.
            keywordMatcher.appendReplacement(keywordSb,
                    Matcher.quoteReplacement(keywordMatcher.group(1).toLowerCase() + keywordMatcher.group(2)));
        }
        keywordMatcher.appendTail(keywordSb);
        css = keywordSb.toString();

        // Remove the spaces after the things that should not have spaces after them.
        css = css.replaceAll("([!{}:;>+\\(\\[,])\\s+", "$1");

        // remove unnecessary semicolons
        css = css.replaceAll(";+}", "}");

        // Replace 0(px,em) with 0. (don't replace seconds are they are needed for transitions to be valid)
        String oldCss;
        p = Pattern.compile("(?i)(^|: ?)((?:[0-9a-z-.]+ )*?)?(?:0?\\.)?0(?:px|em|in|cm|mm|pc|pt|ex|deg|g?rad|k?hz)");
        do {
          oldCss = css;
          m = p.matcher(css);
          css = m.replaceAll("$1$20");
        } while (!(css.equals(oldCss)));

        // We do the same with % but don't replace the 0% in keyframes
        p = Pattern.compile("(?i)(: ?)((?:[0-9a-z-.]+ )*?)?(?:0?\\.)?0(?:%)");
        do {
          oldCss = css;
          m = p.matcher(css);
          css = m.replaceAll("$1$20");
        } while (!(css.equals(oldCss)));
        
        //Replace the keyframe 100% step with 'to' which is shorter
        p = Pattern.compile("(?i)(^|,|\\{) ?(?:100% ?\\{)");
        do {
          oldCss = css;
          m = p.matcher(css);
          css = m.replaceAll("$1to{");
        } while (!(css.equals(oldCss)));

        // Replace 0(px,em,%) with 0 inside groups (e.g. -MOZ-RADIAL-GRADIENT(CENTER 45DEG, CIRCLE CLOSEST-SIDE, ORANGE 0%, RED 100%))
        p = Pattern.compile("(?i)\\( ?((?:[0-9a-z-.]+[ ,])*)?(?:0?\\.)?0(?:px|em|%|in|cm|mm|pc|pt|ex|deg|g?rad|m?s|k?hz)");
        do {
          oldCss = css;
          m = p.matcher(css);
          css = m.replaceAll("($10");
        } while (!(css.equals(oldCss)));

        // Replace x.0(px,em,%) with x(px,em,%).
        css = css.replaceAll("([0-9])\\.0(px|em|%|in|cm|mm|pc|pt|ex|deg|m?s|g?rad|k?hz| |;)", "$1$2");

        // Replace 0 0 0 0; with 0.
        css = css.replaceAll(":0 0 0 0(;|})", ":0$1");
        css = css.replaceAll(":0 0 0(;|})", ":0$1");
        css = css.replaceAll("(?<!flex):0 0(;|})", ":0$1");


        // Replace background-position:0; with background-position:0 0;
        // same for transform-origin
        sb = new StringBuffer();
        p = Pattern.compile("(?i)(background-position|webkit-mask-position|transform-origin|webkit-transform-origin|moz-transform-origin|o-transform-origin|ms-transform-origin):0(;|})");
        m = p.matcher(css);
        while (m.find()) {
            m.appendReplacement(sb, m.group(1).toLowerCase() + ":0 0" + m.group(2));
        }
        m.appendTail(sb);
        css = sb.toString();

        // Replace 0.6 to .6, but only when preceded by : or a white-space
        css = css.replaceAll("(:|\\s)0+\\.(\\d+)", "$1.$2");

        // Shorten colors from rgb(51,102,153) to #336699
        // This makes it more likely that it'll get further compressed in the next step.
        p = Pattern.compile("rgb\\s*\\(\\s*([0-9,\\s]+)\\s*\\)");
        m = p.matcher(css);
        sb = new StringBuffer();
        while (m.find()) {
            String[] rgbcolors = m.group(1).split(",");
            StringBuffer hexcolor = new StringBuffer("#");
            for (i = 0; i < rgbcolors.length; i++) {
                int val = Integer.parseInt(rgbcolors[i]);
                if (val < 16) {
                    hexcolor.append("0");
                }

                // If someone passes an RGB value that's too big to express in two characters, round down.
                // Probably should throw out a warning here, but generating valid CSS is a bigger concern.
                if (val > 255) {
                    val = 255;
                }
                hexcolor.append(Integer.toHexString(val));
            }
            m.appendReplacement(sb, hexcolor.toString());
        }
        m.appendTail(sb);
        css = sb.toString();

        // Shorten colors from #AABBCC to #ABC. Note that we want to make sure
        // the color is not preceded by either ", " or =. Indeed, the property
        //     filter: chroma(color="#FFFFFF");
        // would become
        //     filter: chroma(color="#FFF");
        // which makes the filter break in IE.
        // We also want to make sure we're only compressing #AABBCC patterns inside { }, not id selectors ( #FAABAC {} )
        // We also want to avoid compressing invalid values (e.g. #AABBCCD to #ABCD)
        p = Pattern.compile("(\\=\\s*?[\"']?)?" + "#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])" + "(:?\\}|[^0-9a-fA-F{][^{]*?\\})");

        m = p.matcher(css);
        sb = new StringBuffer();
        int index = 0;

        while (m.find(index)) {

            sb.append(css.substring(index, m.start()));

            boolean isFilter = (m.group(1) != null && !"".equals(m.group(1)));

            if (isFilter) {
                // Restore, as is. Compression will break filters
                sb.append(m.group(1) + "#" + m.group(2) + m.group(3) + m.group(4) + m.group(5) + m.group(6) + m.group(7));
            } else {
                if( m.group(2).equalsIgnoreCase(m.group(3)) &&
                    m.group(4).equalsIgnoreCase(m.group(5)) &&
                    m.group(6).equalsIgnoreCase(m.group(7))) {

                    // #AABBCC pattern
                    sb.append("#" + (m.group(3) + m.group(5) + m.group(7)).toLowerCase());

                } else {

                    // Non-compressible color, restore, but lower case.
                    sb.append("#" + (m.group(2) + m.group(3) + m.group(4) + m.group(5) + m.group(6) + m.group(7)).toLowerCase());
                }
            }

            index = m.end(7);
        }

        sb.append(css.substring(index));
        css = sb.toString();

        // Replace #f00 -> red
        css = css.replaceAll("(:|\\s)(#f00)(;|})", "$1red$3");
        // Replace other short color keywords
        css = css.replaceAll("(:|\\s)(#000080)(;|})", "$1navy$3");
        css = css.replaceAll("(:|\\s)(#808080)(;|})", "$1gray$3");
        css = css.replaceAll("(:|\\s)(#808000)(;|})", "$1olive$3");
        css = css.replaceAll("(:|\\s)(#800080)(;|})", "$1purple$3");
        css = css.replaceAll("(:|\\s)(#c0c0c0)(;|})", "$1silver$3");
        css = css.replaceAll("(:|\\s)(#008080)(;|})", "$1teal$3");
        css = css.replaceAll("(:|\\s)(#ffa500)(;|})", "$1orange$3");
        css = css.replaceAll("(:|\\s)(#800000)(;|})", "$1maroon$3");

        // border: none -> border:0
        sb = new StringBuffer();
        p = Pattern.compile("(?i)(border|border-top|border-right|border-bottom|border-left|outline|background):none(;|})");
        m = p.matcher(css);
        while (m.find()) {
            m.appendReplacement(sb, m.group(1).toLowerCase() + ":0" + m.group(2));
        }
        m.appendTail(sb);
        css = sb.toString();

        // shorter opacity IE filter
        css = css.replaceAll("(?i)progid:DXImageTransform.Microsoft.Alpha\\(Opacity=", "alpha(opacity=");

        // Find a fraction that is used for Opera's -o-device-pixel-ratio query
        // Add token to add the "\" back in later
        css = css.replaceAll("\\(([\\-A-Za-z]+):([0-9]+)\\/([0-9]+)\\)", "($1:$2___YUI_QUERY_FRACTION___$3)");

        // Remove empty rules, but keep empty '@layer' blocks: an empty '@layer name {}'
        // still declares the layer and fixes its position in the cascade order, so
        // removing it would change rendering. Every other at-rule (e.g. '@media',
        // '@supports') does nothing at all when its body is empty, so those are still
        // removed, same as a plain empty rule.
        //
        // A plain char-class exclusion of '@' is not enough to implement even just the
        // '@layer' exception, because an unanchored regex would just resume matching
        // right after the '@' (e.g. deleting "layer utilities {}" out of
        // "@layer utilities {}" and leaving a stray "@" behind). So the three cases are
        // matched as explicit alternatives instead: an '@layer' prelude is kept
        // verbatim; any other at-rule prelude is matched and deleted as one unit (so its
        // '@' is never separated from the rest and left stranded); a plain prelude is
        // deleted as before.
        sb = new StringBuffer();
        p = Pattern.compile("(?i)(@layer[^\\}\\{/;]*\\{\\})|(@[^\\}\\{/;]*\\{\\}|[^\\}\\{/;@]+\\{\\})");
        m = p.matcher(css);
        while (m.find()) {
            if (m.group(1) != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1)));
            } else {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        css = sb.toString();

        // Add "\" back to fix Opera -o-device-pixel-ratio query
        css = css.replaceAll("___YUI_QUERY_FRACTION___", "/");

        // Replace multiple semi-colons in a row by a single one
        // See SF bug #1980989
        css = css.replaceAll(";;+", ";");

        // restore preserved comments and strings
        for(i = 0, max = preservedTokens.size(); i < max; i++) {
            css = css.replace("___YUICSSMIN_PRESERVED_TOKEN_" + i + "___", preservedTokens.get(i).toString());
        }

        // Add spaces back in between operators for css calc function
        // https://developer.mozilla.org/en-US/docs/Web/CSS/calc
        // Added by Eric Arnol-Martin (earnolmartin@gmail.com)
        css = respaceCalcOperators(css);

        // Insert linebreaks for source control tools that don't like long lines.
        // This is done after token restoration so that line lengths are accurate.
        // We track string state to avoid inserting linebreaks inside string literals.
        if (linebreakpos >= 0) {
            i = 0;
            int linestartpos = 0;
            sb = new StringBuffer(css);
            boolean inString = false;
            char stringChar = 0;

            while (i < sb.length()) {
                char c = sb.charAt(i);

                // Step over comments before looking at quotes. A quote inside a
                // comment is not a string delimiter, and the tracking below has no
                // way to know that on its own: one unpaired quote in a preserved
                // "/*! ... */" banner inverted it for the rest of the file, and a
                // linebreak then landed inside a real string literal - a parse error
                // in CSS, so the declaration is dropped. Measured, exit 0:
                // /*! say "hi */a{content:"aaaa...}bbbb"} broke inside the string.
                if (!inString && c == '/' && i + 1 < sb.length() && sb.charAt(i + 1) == '*') {
                    int commentEnd = sb.indexOf("*/", i + 2);
                    i = commentEnd < 0 ? sb.length() : commentEnd + 2;
                    continue;
                }

                // Track whether we're inside a string literal
                if (!inString && (c == '"' || c == '\'')) {
                    inString = true;
                    stringChar = c;
                } else if (inString && c == stringChar) {
                    // Check for escaped quote (look back for odd number of backslashes)
                    int backslashCount = 0;
                    int j = i - 1;
                    while (j >= 0 && sb.charAt(j) == '\\') {
                        backslashCount++;
                        j--;
                    }
                    if (backslashCount % 2 == 0) {
                        // Not escaped, end of string
                        inString = false;
                    }
                }

                i++;

                // Only insert linebreak at '}' if not inside a string
                if (c == '}' && !inString && i - linestartpos > linebreakpos) {
                    sb.insert(i, '\n');
                    i++; // Skip the newly inserted newline
                    linestartpos = i; // New line starts after the newline character
                }
            }

            css = sb.toString();
        }

        // Trim the final string (for any leading or trailing white spaces)
        css = css.trim();

        // Write the output...
        out.write(css);
    }

    /**
     * Restores the whitespace that calc() requires around its operators.
     *
     * The passes above strip the spaces around '+', '-', '*' and '/', but calc() only accepts
     * '+' and '-' when they are surrounded by whitespace. Putting them back cannot be done with
     * a lookbehind on the previous character, because a hyphen or a letter is just as likely to
     * belong to an identifier -- a custom property such as --x1-y, or a nested argument such as
     * env(safe-area-inset-top) -- as it is to end an operand. So each expression is walked once,
     * consuming whole numbers and whole identifiers, and an operator is only respaced when the
     * token before it actually ends an operand.
     */
    private static String respaceCalcOperators(String css) {
        StringBuffer sb = new StringBuffer(css.length());
        int pos = 0;
        while (pos < css.length()) {
            int start = css.indexOf("calc(", pos);
            if (start < 0) {
                sb.append(css, pos, css.length());
                break;
            }
            int open = start + "calc(".length() - 1;
            int close = findMatchingParen(css, open);
            if (close < 0) {
                // Unbalanced - leave it alone and keep looking after the "calc(".
                sb.append(css, pos, open + 1);
                pos = open + 1;
                continue;
            }
            sb.append(css, pos, open + 1);
            respaceExpression(css, open + 1, close, sb);
            sb.append(')');
            pos = close + 1;
        }
        return sb.toString();
    }

    /**
     * Returns the index of the ')' matching the '(' at openPos, or -1 if there is none.
     */
    private static int findMatchingParen(String css, int openPos) {
        int depth = 0;
        for (int i = openPos; i < css.length(); i++) {
            char c = css.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Appends css[from, to) to sb, surrounding every binary operator with a single space.
     */
    private static void respaceExpression(String css, int from, int to, StringBuffer sb) {
        int floor = sb.length();
        boolean operandEnd = false;
        int i = from;
        while (i < to) {
            char c = css.charAt(i);
            if (operandEnd && isCalcOperator(c)) {
                while (sb.length() > floor && isCssSpace(sb.charAt(sb.length() - 1))) {
                    sb.setLength(sb.length() - 1);
                }
                sb.append(' ').append(c).append(' ');
                i++;
                while (i < to && isCssSpace(css.charAt(i))) {
                    i++;
                }
                operandEnd = false;
            } else if (isCssSpace(c)) {
                // Whitespace separates tokens but does not end an operand.
                sb.append(c);
                i++;
            } else if (startsNumber(css, i, to)) {
                i = appendNumber(css, i, to, sb);
                operandEnd = true;
            } else if (startsIdentifier(css.charAt(i))) {
                i = appendIdentifier(css, i, to, sb);
                operandEnd = true;
            } else {
                sb.append(c);
                operandEnd = (c == ')');
                i++;
            }
        }
    }

    private static boolean isCalcOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    private static boolean isCssSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * A number may carry a sign here: an operator in that position was already respaced above,
     * so a '+' or '-' still seen in front of a digit is a unary sign.
     */
    private static boolean startsNumber(String css, int i, int to) {
        char c = css.charAt(i);
        if (isDigit(c)) {
            return true;
        }
        if (c == '.') {
            return i + 1 < to && isDigit(css.charAt(i + 1));
        }
        if (c == '+' || c == '-') {
            if (i + 1 >= to) {
                return false;
            }
            char next = css.charAt(i + 1);
            return isDigit(next) || (next == '.' && i + 2 < to && isDigit(css.charAt(i + 2)));
        }
        return false;
    }

    /**
     * Consumes a number and the unit that follows it. The unit is taken as letters only, so that
     * "100px-30px" splits into two dimensions rather than one with the unit "px-30px" -- which is
     * what the CSS tokenizer would do, and the reason calc() demands the spaces in the first place.
     */
    private static int appendNumber(String css, int i, int to, StringBuffer sb) {
        char c = css.charAt(i);
        if (c == '+' || c == '-') {
            sb.append(c);
            i++;
        }
        while (i < to && (isDigit(css.charAt(i)) || css.charAt(i) == '.')) {
            sb.append(css.charAt(i));
            i++;
        }
        if (i < to && css.charAt(i) == '%') {
            sb.append('%');
            return i + 1;
        }
        while (i < to && isLetter(css.charAt(i))) {
            sb.append(css.charAt(i));
            i++;
        }
        return i;
    }

    private static boolean startsIdentifier(char c) {
        return isIdentifierChar(c) && !isDigit(c);
    }

    /**
     * Consumes a whole identifier, hyphens and digits included, so that names such as
     * --x1-y or safe-area-inset-top are never split apart.
     */
    private static int appendIdentifier(String css, int i, int to, StringBuffer sb) {
        while (i < to && isIdentifierChar(css.charAt(i))) {
            sb.append(css.charAt(i));
            i++;
        }
        return i;
    }

    private static boolean isIdentifierChar(char c) {
        return isLetter(c) || isDigit(c) || c == '-' || c == '_' || c == '\\' || c >= 0x80;
    }
}
