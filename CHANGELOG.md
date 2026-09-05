# Changelog

All notable changes to YUI Compressor will be documented in this file.

## [2.4.11-SNAPSHOT]

This release fixes a set of correctness defects in both the CSS and JavaScript
compressors, most of them capable of emitting invalid or behaviourally-changed
output while exiting successfully. The public API of `JavaScriptCompressor`
and `CssCompressor` (constructors and `compress` overloads) is unchanged.

### Fixed (CSS)
- At-rule preludes are no longer corrupted into function tokens: `@container <name> (...)`,
  `@supports ... not (...)`, and `@scope ... to (...)` now keep the required space before `(`
- Custom property values (`--foo: ...`) and `@property` descriptor values are now preserved
  verbatim, including internal whitespace, instead of being reformatted like ordinary declarations.
  This holds when a preserved `/*!` comment sits between the declaration and its `{` or `;`
  as well: the first version of this fix accepted only `{` and `;` as the preceding character,
  so `:root{/*! v1 */--brand:#ff0000}` still became `--brand:red` and `:root{/*! x */--pad:0px}`
  still became `--pad:0`, which breaks `calc(var(--pad) + 1px)`
- Comment collection is string- and URL-aware, fixing two defects that were really one: the scan
  was context-free and ran before string and URL handling. A comment-looking span inside a string
  or an unquoted `url()` was collected as a comment, and the resulting placeholder then sat
  mid-value while *looking* like a leading banner comment - which defeated the at-rule and
  declaration boundary checks below at all four of their call sites.
  `url(/x/`+`/*!k*/`+`@charset "y";.png)` invented a stylesheet encoding out of a URL fragment and
  deleted the fragment from the URL
- **An unterminated `/*` no longer truncates the stylesheet.** The same scan replaced everything
  from an unclosed `/*` to end-of-input with an internal placeholder that nothing removed, so the
  output was cut short, the following rules were lost, internal scaffolding was emitted into
  shippable CSS, and the exit code was 0. `a{content:"/*"}` - valid CSS - was enough to trigger it,
  as was a data URL carrying an unclosed `/*`. Both are now handled structurally. A `/*` that has
  no `*/` after it anywhere, and that sits outside any string and outside any *unquoted* `url()`,
  is rejected with an error naming the offset: browsers consume such a comment to end-of-input, so
  the stylesheet is already broken either way, and silently discarding the rest of the file is the
  behaviour this release exists to remove. Inside a quoted `url()` a comment is an ordinary
  comment, so `url("x" /* oops)` reaches that error too
- **A comment inside a quoted `url()` no longer corrupts the stylesheet.** Fixing the item above
  introduced its own context-free scan: every `url(` was treated as a raw url-token and scanned for
  the first `)`, which is only correct for the unquoted form. Per CSS Syntax Level 3 §4.3.4, `url(`
  followed by a quote is an ordinary function token whose contents are ordinary tokens, comments
  included. `url("a.png" /* legacy: url(b.png) /* keep */)` desynced the scan, which then deleted
  the only `*/` in the file and shipped an unterminated `/*` with exit code 0 - so a browser
  discarded every rule that followed. The same desync made the unterminated-comment error fire on
  valid stylesheets such as `a{background:url("x" /* ) " */)}b{content:"/*"}`, and left ordinary
  comments inside quoted `url()`s in the output. Only the unquoted form is scanned raw now
- A comment inside `calc()`, `progid:…Matrix()` or a `data:` URL no longer leaks internal
  scaffolding into the output. Those spans are captured whole and restored at the very end, after
  the pass that resolves comment markers, so the marker itself was emitted: valid CSS
  `a{width:calc(100% /* n */ - 10px)}` came out as
  `a{width:calc(100% / *___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_0___ * / - 10px)}` with exit code
  0 - the calc respacer reads the marker's own `/*` and `*/` as division and multiplication. The
  markers are now settled where the spans are captured, keeping `/*!` comments and dropping the
  rest, exactly as elsewhere
- **`--line-break` now decides where a rule ends using the same region model as comment
  collection**, rather than its own. A raw newline inside a CSS string is a parse error, so every
  disagreement between those two models dropped a declaration from a valid stylesheet with exit
  code 0. There were three, and the first two were found one after the other in the same file:
  the pass tracked strings but not comments, so a single unpaired quote in a preserved `/*! … */`
  banner inverted its tracking for the rest of the file and the newline landed inside a real
  string; teaching it comments alone moved the fault one layer down, because a `/*` inside an
  *unquoted* `url()` is ordinary URL content rather than a comment, so
  `a{background:url(/x/*p.png)}` followed by `b{content:"*/z"}` put the newline inside the *next*
  rule's string; and separately, a `}` inside an unquoted `url()` - legal there, since a url-token
  stops only at `)`, whitespace, a quote or `(` - was read as the end of a rule and broke the line
  inside the URL, which is precisely what a url-token may not contain. The pass now steps over
  strings, `url()` tokens and comments with the same helpers `collectComments` uses, so the two
  cannot drift apart again. On where those regions *end*, fuzzing 68,383 (source, width) pairs
  against an independent §4.3 tokenizer found no case that ends one early — an unterminated
  string, URL or comment runs to the end of input, which here only costs a longer line. That is
  what was tested, not a property of the design, and it says nothing about where a region
  *begins*: an escaped quote starts one that is not there, which is a known defect listed below
- **White space inside a non-base64 `data:` URL is no longer deleted.** Preserving a `data:` URL
  stripped white space from the whole token, including the contents of its quoted string. For a
  base64 payload that is a convenience; for any other payload the data is literal, so it destroyed
  author bytes on valid CSS with exit code 0:
  `url("data:image/svg+xml,<svg viewBox='0 0 24 24'><text>hello world</text></svg>")` came out with
  `viewBox='002424'` - one invalid value where the grammar wants four numbers - and
  `<text>helloworld</text>`. Percent-encoded payloads (`%20`) were never affected, which is why
  this survived: that is the machine-generated style and the literal space is the hand-written one.
  White space is still removed *outside* the quotes, and still joined *inside* them when the
  payload is base64 (RFC 2397 puts `;base64` last in the header, after any media-type parameter,
  and such a payload's white space is insignificant). One deliberate consequence: a non-base64
  quoted `data:` URL split across lines is no longer joined - a newline inside a CSS string is a
  parse error, so that input was already invalid, and the accidental repair was the same operation
  as the corruption. Unquoted `data:` URLs are unchanged, since white space inside a url-token
  makes it a bad-url-token anyway
- At-rules and declarations are now only recognised where one can actually begin - after `}`,
  `;`, `{`, a preserved-token placeholder, or the start of the stylesheet. Matching their literal
  text anywhere it appeared meant `a { background: url(/img/@property.png) }` was treated as an
  `@property` block and left the *following* rule entirely unminified; `url(/img/@MEDIA.png)` was
  lowercased even though URL paths are case-sensitive; and an `@charset "x";` inside an
  unpreserved `url()` was hoisted out of the URL to the top of the stylesheet
- Empty `@layer` blocks (e.g. `@layer base, components, utilities;`) are no longer deleted; they
  declare cascade order even with no rules inside
- Modern at-rule names (`@container`, `@layer`, `@scope`, `@starting-style`, `@supports`) are now
  normalised to lowercase, extending the existing at-rule lowercasing list. `@property` is in that
  list but is **not** lowercased in practice: the whole `@property` block is replaced by a preserved
  token before the lowercasing pass runs, so `@PROPERTY --c {...}` passes through unchanged. That
  whole-block preservation is deliberate (a descriptor value is an arbitrary token stream), so the
  claim is corrected here rather than the behaviour
- An escaped `@` in a selector no longer corrupts the rule after an empty one. Empty-rule removal
  matched an at-rule prelude with a separate `@`-anchored alternative, and `@` is an ordinary
  character in a class name once escaped, so the only `@` in `.\@container{}` sat in the middle of
  a plain prelude that no alternative could match. The `@` alternative matched from the `@` onward,
  and deleting `@container{}` welded the leftover `.\` to the next rule: `.\@container{}p{color:red}`
  came out as `.\p{color:red}`. Tailwind emits such class names by the hundred (`.\@lg\:block`)
- An unquoted `url()` containing `{`, `}` or `;` no longer leaves the rest of the stylesheet
  unminified. The custom-property scan had no notion of strings or URL tokens, so `url(/x/;--y.png)`
  put a `--` straight after a `;` and read as a declaration; the search for its `:` then ran out of
  the URL into the next rule's `b:hover`, and the value scan ran from there to end-of-input,
  preserving all of it verbatim with exit code 0. An unbalanced brace did the same to a real custom
  property, `url(a}b.png)` driving the value scan past the `}` that ended the declaration. The scan
  now uses the same string/URL region model as comment collection and line breaking
- Minifying a stylesheet of `@property` rules is linear again, not quadratic. Those blocks are
  preserved whole by this release, so a stylesheet of them collapses to one long run of placeholder
  text with no brace in it, and the empty-rule prelude had to be retried from every offset inside
  the run. 800 `@property` rules took 3.2s; doubling the count multiplied the time by 3.9

### Fixed (JavaScript)
- Optional chaining is preserved and no longer widened: `a?.b.c` now stays `a?.b.c` instead of
  becoming `a?.b?.c`. The two diverge when `a` is non-null but `a.b` is null: `a?.b.c` throws
  a `TypeError` on the plain `.c` access, while the widened `a?.b?.c` would silently swallow
  that error and return `undefined` instead.
  The first version of this fix covered the `?.` operator itself but not chains sitting inside a
  node type that reached the `toSource()` fallback, where `?.` was still deleted outright - see
  the next entry, which closes that
- Node types with no handler no longer emit un-munged identifiers. `MungedCodeGenerator`'s
  `default:` arm calls `toSource()`, which re-prints the *original* source of the whole subtree,
  so every identifier in it kept its pre-munge spelling while its declaration was munged -
  silently turning locals into globals - and `?.` was dropped. `??`, `??=`, `||=`, `&&=`, `**=`
  and `debugger` all reached it. `function f(alpha, beta) { var gamma = alpha ?? beta; ... }`
  compressed to `function f(c,b){var a=alpha ?? beta;...}`, and
  `config.timeout ?? config.server?.timeout` to `config.server.timeout`, turning a safe
  `undefined` into a `TypeError`. All six are now handled, and a new opt-in strict mode
  (`-Dyuicompressor.strict=true`) makes any *future* unhandled node type throw instead of degrading
  silently - the test suite runs the whole fixture corpus plus a modern-syntax table with it on
- Default and rest parameters are no longer dropped. `function f(a=1)` compressed to
  `function f(a)`, changing `f()` from `1` to `undefined`, and `function f(...args)` to
  `function f(args)`, turning an array of the trailing arguments into a single positional
  parameter and changing `f.length`. A destructuring pattern that carries a default
  (`function f({b}={})`) is the one form Rhino records nowhere; the compressor now fails loudly
  on it rather than emitting a parameter list quietly missing it
- Shorthand properties no longer have their key renamed. `{b}` is shorthand for `{b:b}`, so the
  identifier is both the property key and the binding; munging it renamed the property in an
  object literal and, in a destructuring pattern, read a property the caller never passed -
  `function f({b}){return b;}` called with `{b:7}` returned `undefined`. It now expands to
  `{b:a}` when the binding is munged, and stays shorthand when it is not.
  The same applies to shorthand carrying a default, `{b = 1}`, which Rhino reports as
  *not* shorthand and which therefore went on corrupting after the first version of this fix. In
  that form the same `Name` object is both key and binding, so the key guard suppressed munging on
  the binding while body references were munged. All three positions are fixed - destructured
  parameter, `var` destructuring, and assignment destructuring. The last was silent:
  `({someKey = 5} = o)` returned `undefined` instead of `5` and passed `node --check`
- Trailing array elisions keep their slot. A comma in an array literal is a separator, so a
  trailing one is not an element - `[a,b,]` and `[a,b]` are both length 2 - which means a trailing
  hole needs a comma of its own. `[, , x, , ]` was emitted as `[,,b,]`, changing the array from
  length 4 to length 3
- BigInt literals (`10n`) are handled rather than routed through the `toSource()` fallback. This
  was harmless by luck in the default path, but strict mode could not compress any file containing
  one
- `for each (var b in a)` emits its keyword before the parenthesis. It was emitted as
  `for(var a each in b)`, which this compressor's own parser rejects - invalid output with a
  success exit code
- `catch (e if e instanceof TypeError)` keeps its guard, which was silently dropped, widening the
  catch to every exception
- Generator object methods (`var o = { *gen(){ yield 1; } };`) no longer crash the compressor
  with a `ClassCastException`
- Getter and setter properties no longer fail open: a non-function right-hand side used to emit
  `{get x}`, which is not valid JavaScript
- Optional catch binding (`catch {}`) is no longer emitted as the invalid `catch()`
- Template literal and regex literal contents are no longer rewritten, so internal whitespace
  (including runs of spaces) round-trips unchanged
- `--line-break` no longer splits identifiers or string literals across the break. Breaks are
  recorded at statement boundaries, but a separating space inserted later (see the operator-merge
  entry below) shifted the text without adjusting those offsets, so each break landed one
  character early per separator inserted before it. At `--line-break 20`,
  `var q = a + + +function(){ var s = "hello"; }();` cut the string literal in half - a hard
  `SyntaxError` - while the identifier variant produced output that still *parsed*, as two
  statements naming two different variables
- `yield*` no longer loses its delegation star (it previously fell back to `toSource()`, which drops it)
- Labeled statements no longer crash the compressor with a `ClassCastException`
- Fixed operator/operand token merging that produced invalid or behaviour-changing output:
  `a + +b` no longer collapses into `a++b`, and `- -a` / `+ +c` no longer collapse into the
  pre-decrement/pre-increment `--a` / `++c`, which would silently mutate the operand
- Fixed comment injection in minified output: a `/` operator directly before a regex literal no
  longer forms a `//` line comment, and a `<` before `!--` no longer forms the Annex B `<!--`
  comment opener. Both are single-line comment openers, and since minified output is a single
  line, either one swallowed everything after it in the file, not merely the rest of the
  statement
- Locals visible to `eval` or `with` are no longer munged, restoring the safety the README promises
  for those constructs (direct `eval` can read any local in its scope chain by name; `with` can
  dynamically shadow one)
- A function's own name is no longer handed out as a free symbol. `ScopeBuilder` never declared it,
  so the munger treated it as unused: `function f(x){...}` beside six locals produced `var f=1`, and
  the `f(...)` call next to it then read the variable. A declaration's name is now declared in the
  enclosing scope and a named function expression's in its own, which is also what makes a recursive
  self-call resolve to the function rather than to the outer variable - `var s = function s(n){...
  s(n-1) ...}` had its self-call rewritten to the outer binding, so reassigning that binding changed
  what the recursion called. The name is reserved rather than renamed, in the declaring scope and in
  every enclosing scope that gets munged, because an outer variable munged to the same spelling is
  shadowed by the function inside its own body. jQuery 1.6.4 costs 45 bytes (0.04%) for this
- Strict mode reads its property as a boolean. `isStrict()` only tested it for non-null, so
  `-Dyuicompressor.strict=false` - and `0`, `off`, `no`, and the empty string a shell leaves behind
  for a bare `-Dyuicompressor.strict` - all turned strict mode ON, and the compressor refused files
  it compresses fine by default. Only `true` enables it now

### Fixed (command line)
- A refusal no longer destroys the file it was writing. The destination was opened, and therefore
  truncated, before the compression that can fail, so a stylesheet this release declines to guess at
  left an empty output file - and with `-o` pointing at the input, an empty source file: 29 bytes to
  0. The compressed text is now produced in full before the destination is opened
- A refusal is reported as an error, not as an uncaught exception. `IllegalArgumentException` left
  `main` and printed `Exception in thread "main"` with a stack trace over a message written to be
  read; it is now printed as `[ERROR] <file>: <message>` with a non-zero exit
- Every input file reaches stdout. The per-file writer wrapped `System.out` and was closed after the
  first file, so every later file in the same run wrote to a closed stream and was silently
  discarded - `yuicompressor a.css b.css` emitted only `a.css`. Pre-existing, not a regression

### Fixed (Node.js wrapper)
- **The wrapper no longer runs the un-runnable jar.** It took the first file in `target/` whose
  name merely *contained* `yuicompressor`, and `maven-shade-plugin` leaves the pre-shade
  `original-yuicompressor-<version>.jar` right there - 66 KB with no dependencies in it, which
  dies with `NoClassDefFoundError: org/kohsuke/args4j/CmdLineException`. Which of the two won
  depended on `readdir` order, so the package worked or returned nothing depending on the
  filesystem. Only `yuicompressor-<version>.jar` is accepted now, with `-sources` and `-javadoc`
  excluded. Pre-existing, not a regression
- `original-*.jar` is no longer published to npm either: `files` shipped both jars, so the
  broken one travelled with the package
- **A failed compression is no longer reported as success with an empty string.** `err` was
  derived solely from the substring `[ERROR]` appearing in stderr, so anything that killed the
  JVM before the compressor could print that marker - the missing class above, an unreadable jar
  - arrived at the caller as `err === null` and `''` for the compressed output. A non-zero exit
  is now an error regardless of what stderr says
- **`java` missing from `PATH` no longer hangs the caller.** `spawn` emits `'error'` and never
  emits `'exit'`, and nothing listened for it: the callback was never invoked, and the ENOENT
  surfaced as an uncaught exception in whatever code happened to be running. Both `'error'` and a
  broken pipe on the child's stdin are handled now, and exactly one outcome is delivered
- A file that exists but cannot be read (a directory, a permissions failure) reports that error.
  `compress` took the error from `fs.readFile` and discarded it, then passed `undefined` to
  `child.stdin.write`, which threw `ERR_INVALID_ARG_TYPE` from inside the wrapper

### Changed (Node.js tests)
- **The Node.js test suite ran zero assertions and reported success.** Four separate reasons, each
  sufficient on its own: `tests/node/tests.js` resolved its own dependency as `../nodejs/index`
  from `tests/node/`, i.e. `tests/nodejs/index`, which does not exist, so the file could not even
  load; had it loaded, it scanned `tests/` for `<name>.<ext>` + `<name>.<ext>.min` fixture pairs
  and that directory holds none; `yuitest` exits 0 when no tests load; and the CI job that ran it
  carried `continue-on-error: true`. This is why the jar-selection defect above shipped
- Replaced it with `tests/node/wrapper.test.js` on the built-in `node:test` runner (no
  dependency; `yuitest` is dropped), covering what the wrapper actually owns - which jar it
  selects and whether compression reaches the caller. `npm test` now exits non-zero when a test
  fails, and CI no longer ignores it

### Improved
- Function expressions passed as call arguments, object property values, and array elements now
  get scopes and have their parameters munged, closing a gap in `ScopeBuilder`'s traversal;
  jQuery 1.6.4 minified output went from 137,798 to 106,970 bytes
- Redundant brace pairs are gone. Rhino wraps a loop, if- or do-body that declares anything in a
  `Scope`, which does not extend `Block`, so an `instanceof Block` check missed it and wrapped an
  already-braced block in a second pair: `for(...){f();}` came out as `for(...){{f();}}`. jQuery
  1.6.4 measured 106,970 -> 104,815 bytes, with the `{{` count going 1,100 -> 0
- `ScopeBuilder` now traverses default parameter expressions, which Rhino keeps in a side list
  rather than as children. A name read there is a real use, and an `eval` there is a real reason
  not to munge

### Documented (behaviour that was described inaccurately)
- `--preserve-semi` and `--disable-optimizations` are accepted and **ignored**. They are now marked
  as such in the README and in the `compress(...)` javadoc. Implementing them is Release 2 work;
  the Release 1 fix is that a caller passing them is no longer left believing they took effect
- `-v/--verbose` is **mostly** unimplemented, not entirely: the CLI reads it for one informational
  line when an unsupported charset is replaced by UTF-8, and the compressors never read it at all.
  An earlier draft of this entry said it was ignored outright, which was wrong about the flag
  though right about the `compress(...)` parameter
- `"name:nomunge"` hints are **not implemented**: the named symbols are munged anyway and the hint
  string is emitted into the output as a live statement rather than disappearing. The README said
  otherwise
- `--line-break 0` gives a line break after each rule in CSS, but is a **no-op in JavaScript**.
  The README described the JavaScript behaviour that has never existed

### Known limitations (CSS), carried to Release 2

Every entry below is one instance of a single defect class: **a pass that runs without knowing what
region of the document it is in.** That class is what this release was about. Release 1 fixed every
instance that its own changes touched, and every instance that both destroyed data and was
reachable by ordinary authoring; what remains is enumerated here rather than left unknown. Each has
a regression test pinning its current behaviour, so none of them can drift or be half-fixed
unnoticed.

They are not equally serious, and the difference is worth stating plainly, in three groups below.
The first destroys author content and can cost a declaration; it is here on reachability and on the
risk of a seventh behaviour change to this file, both argued in its entry, rather than because the
damage is acceptable. The second rewrites content inside one declaration. The third only leaves a
comment in the output. None of them loses a *rule* it was not asked about, truncates the stylesheet,
or changes the exit code - those failures existed and are fixed.

**Destroys data.** One entry, and it is the most serious thing on this list.

- An **escaped quote in a selector or identifier** starts a string region that is not there. Every
  region scan in the CSS compressor decides a string begins wherever it sees a quote, without
  asking whether that quote is escaped, and `\"` is a valid identifier escape (§4.3.7). The phantom
  region ends at the *opening* quote of the next real string, so the scan then runs inside it, with
  two measured consequences on valid CSS at exit code 0: `--line-break` puts a newline inside a
  string literal, which is a parse error, so that declaration is dropped; and comment collection
  deletes a comment-looking span that is really string content — `content:"keep /* this */ text"`
  becomes `content:"keep text"`. Pre-existing and unchanged by this release.
  Deferred rather than fixed because the *harm* is hard to reach, not because it is small: it needs
  a comment or a `}` inside a later string. The *trigger* is not rare — Tailwind's
  `content-['x']` arbitrary values emit an escaped quote in the generated selector — but on that
  shape the effect is only a span left unminified, plus a following `/*!` banner losing the space
  after its `!`, which is the one part of this that is a regression from this release rather than
  pre-existing. Three scans share the blindness and a backslash *pair* is not an escape, so a fix
  has to count backslashes in three places; that is more behaviour change than this release should
  carry after six corrections to the same file

**Rewrites confined to one declaration.** Wrong output, not just unminified output.

- `calc(` and `progid:…Matrix(` are matched inside strings, and the captured span's placeholder is
  then never resolved. `a{content:"calc(1px + 2px)"}` becomes
  `a{content:"calc(___YUICSSMIN_PRESERVED_TOKEN_0___)"}`, replacing the author's text with internal
  scaffolding. Also reachable through an attribute selector and a `font-family` list. Pre-existing
- `calc()` operator respacing runs after token restoration, when no string, comment or URL is
  protected any more, so it rewrites `calc(` wherever it appears - inside a preserved `/*! … */`
  comment, or inside a URL path. Pre-existing
- Those two are one problem seen from two ends, which is why neither is fixed here: resolving the
  placeholder alone restores the string's real content, which respacing then rewrites instead, so
  the visible scaffolding would become an invisible edit of the author's text. They have to move
  together, and that is a design change rather than a patch
- An unquoted URL whose contents happen to spell a declaration reaches the value optimisers:
  `url(/x/…--y:0px)` has the `0px` shortened to `0` inside the URL. Not reachable by realistic
  URLs - `url(/img/0px-spacer.png)` and `url(/i?w=0px)` are untouched. Pre-existing

**Comments that survive into the output.** A missed optimisation; nothing is altered or lost, and
every trigger is already-invalid CSS.

- After an unterminated string, later comments are not collected, so they are emitted rather than
  stripped. A string closed by a newline behaves the same way, although CSS ends the string there
- After an unclosed `url(`, the same thing, for the rest of the file
- A stray quote inside an unquoted `url()` - a bad-url token - suppresses collection to the end of
  that span, and, since `--line-break` now shares the same region model, suppresses line breaking
  from there on too. The scanner steps over the quoted region deliberately: the alternative ends
  the URL early, which deletes bytes from it in one pass and breaks the line inside it in the
  other - the corruption the release exists to remove. Both symptoms are the same helper failing
  in the same safe direction, which is the point of their sharing it

These three are regressions from the structural comment scanner, not pre-existing behaviour: the
context-free scan it replaced did strip these comments, by not knowing where it was - the same
blindness that truncated whole stylesheets. Leaking a comment on malformed input is what that trade
bought, and it is the safe direction to fail in.

### Changed (Build and tests)
- Migrated the test suite from JUnit 4 to JUnit 5
- The 72 golden fixture pairs (62 CSS, 10 JS) under `src/test/resources` are now actually executed
  by `CssGoldenFileTest` and `JsGoldenFileTest`; they existed in the repository but had never been
  run by any test. 64 are executed (61 CSS, 3 JS); the remaining 8 are quarantined in
  `KNOWN_FAILURES`, each with a reason measured against current output. A further 3 CSS pairs are
  disabled by the repository's pre-existing `.FAIL` convention, which renames the *source* and
  leaves the `.min` golden in place
- Removed an undocumented `.filter(n -> !n.startsWith("_"))` that three test classes carried. It
  excluded 5 mismatching JS golden pairs from every test, was explained nowhere, and was why the
  JS corpus was counted as 4 pairs rather than 9. Those 5 are now in `KNOWN_FAILURES`; between
  them they record four upstream optimisations this generation does not perform (`;}` stripping,
  string-literal merging, quote-character choice, `nomunge` hints), all Release 2 work
- `issue71.js` no longer carries the `.FAIL` suffix: it passes as of this wave, which is what
  `suite.sh` says to do
- Added compressor option coverage, modern CSS/JS regression tests, a round-trip parameter table
  (`ParameterListTest`), a strict-mode fallback guard (`StrictNodeCoverageTest`), and an output
  guard (`JsOutputSyntaxTest`) that runs `node --check` against 9 of the 10 JS fixtures plus a
  comment-injection scanner over all 10. The tenth,
  `promise-catch-finally-issue203.js`, is skipped rather than silently passed: node rejects its
  *source*, so there is nothing to check
- Added `DifferentialExecutionTest`: 34 small, deterministic scripts run under node twice, once
  as source and once compressed, comparing stdout and exit status. `node --check` only proves the
  output parses, and *every* silent-corruption defect in this release produced output that
  parsed. This test found two nobody had reported - the shorthand-property key renaming above,
  and the shorthand-with-default corruption
- This class is **half** of the net, not the net. It detects semantic change under compression
  and nothing else: a compressor that echoed its input, or that had munging disabled entirely,
  would leave it green (both measured). The golden fixtures and the exact-output tests are what
  cover the other half - disabling munging fails 25 tests across five classes
- A missing `node` skips; a broken `node` fails. Both availability probes were
  `catch (Exception e) { return false; }`, so a `node` that was present but broken, sandboxed or
  hanging disabled the whole differential net silently and still reported success. Only "not on
  `PATH`" skips now. Every `node` process is bounded by a timeout and writes to files rather than
  pipes, so a looping or output-heavy script fails instead of hanging the build
- Skips are counted honestly. Disabling whole methods with `@EnabledIf` hid 35 real executions
  behind 3 skip lines; they are now skipped per case, so without node on `PATH` the report reads
  46 skipped rather than 5
- A size and shape pin for `jquery-1.6.4.js` (`104,815` bytes, zero `{{`, 1,259 `;}`). It is
  quarantined from the golden comparison, which had left the only large real-world fixture with no
  byte-level guard at all: reverting the redundant-brace fix, worth 2,200 bytes on this file, left
  the golden test green
- Test suite grew from 163 to 526 tests (3 skipped: the two `ES6SupportTest` cases Rhino cannot
  parse, plus the fixture whose source node rejects). Without node on `PATH`, 46 are skipped and
  the rest still pass
- Updated Maven plugins to current releases; removed the leftover Ant build (`build.xml`,
  `ant.properties`) and Travis CI configuration (`.travis.yml`)

## [2.4.8]

### Fixed
- Fix "important" and conditional comment processing
- Fix a bug in the support for JS 1.7 style getters/setters

### Improved
- Better compliance and results in CSS compression (@danbeam, @faisalman, @killsaw, @ademey)
- Now minifies "border-left" in CSS (@sbertrang)
- Include filename in warning and error output (@danielbeardsley)
- Many improvements to parameter parsing and batch modes (@bmouw, @bandesz, @ryansully)
- Include jQuery as part of our test suite (@apm)
- Trim trailing commas where possible (@nlalevee)

## [2.4.7]

### Fixed
- Handle data urls without blowing up Java memory (regex)
- Fixed issue where we were breaking #AABBCC id selectors, with the #AABBCC -> #ABC color compression

### Changed
- Updated docs to reflect Java >= 1.5 required for CssCompressor

## [2.4.6]

### Added
- Show usage information when started without arguments

## [2.4.5]

### Changed
- Default file encoding changed from system default to UTF-8
- Errors/messages/usage info all are sent to stderr

### Fixed
- Removed unnecessary warning about short undeclared global symbols
- $ in CSS files doesn't throw exceptions
- White space in ! important comments preserved in CSS
- Fix in greedy empty CSS declaration blocks removal
- Safe handling of strings and comments in CSS files
- Fixed transform-origin: 0 0 [bug 2528060]

### Added
- Added support for processing multiple files with a single invokation
- Shorter alpha opacity CSS filters
- Shorter Mac/IE5 hack -> /*\*/ hack {mac: 1} /**/
- JS port of the CSS minifier
- Safe @media queries handling
- Stripping the trailing ; in CSS declaration blocks
- Shorter border:none->0 where applicable
- tests++

## [2.4.4]

### Note
- Interim 2.4.5 release

## [2.4.3]

### Changed
- Changed custodian to ci-tools@

## [2.4.2]

### Fixed
- Preserved comments shouldn't prevent obfuscation (Thanks to Matjaz Lipus)

## [2.4.1]

### Improved
- Use preferentially lower case letters for obfuscated variable names. Since JavaScript keywords use lower case letters most often, this improves the efficiency of any compression algorithm (gzipping) used after minification
- Don't append a semi-colon at the end of a JavaScript file when the last token is a special comment

## [2.4]

### Added
- Allowed the YUI Compressor (which uses a modified version of Rhino) to work alongside the original (unmodified) rhino library by using a custom class loader
- Added all that's necessary to build the YUI Compressor to the downloable package

### Fixed
- Fixed unnecessary white space after return / typeof when possible

## [2.3.6]

### Fixed
- Fixed a few minor bugs with the CSS compressor

### Changed
- Changed packaging. The original Rhino library, which is used to build the YUI Compressor, is not part of the downloadable archive. Too many people put it in their classpath, generating a lot of invalid bugs

## [2.3.5]

### Added
- Added a warning when more than one 'var' statement is used in a single scope. Automatic coalescence is extremely complicated, and would be unsafe if not done properly

## [2.3.4]

### Changed
- Expanded the list of reserved words used by isValidIdentifier()

## [2.3.3]

### Added
- C-style comments starting with /*! are preserved. This is especially useful with comments containing copyright/license information

## [2.3.2]

### Fixed
- Compressing an empty JS file throws an error [SourceForge bug #1884207]
- When a string is the first token in a function body, it was removed from the compressed file [SourceForge bug #1884314]

## [2.3.1]

### Added
- Added test against list of reserved words in method isValidIdentifier

## [2.3]

### Added
- Always output a ';' at the end of a minified JavaScript file. This allows the concatenating of several minified files without the fear of introducing a syntax error
- Transform obj["foo"] into obj.foo whenever possible, saving 3 bytes
- Transform 'foo': ... into foo: ... whenever possible, saving 2 bytes
- Added support for multi-line string literals [SourceForge bug #1871453]
- Added support for unescaped slashes inside character classes in regexp

### Fixed
- Removed all System.exit() statements. Throw exceptions instead. This is especially useful when running the compressor from within a J2EE container [SourceForge bug #1834750]
- Preserve the escaping for an octal representation of a character in string literals [SourceForge bug #1844894]

### CSS
- Preserve comments that hide CSS rules from IE Mac:
  ```css
  /* Hides from IE-mac \*/
  ...
  /* End hide from IE-mac */
  ```
- Added support for box model hack [SourceForge bug #1862107]:
  ```css
  div.content {
    width:400px;
    voice-family: "\"}\"";
    voice-family:inherit;
    width:300px;
  }
  ```

### Performance
- Minor performance improvements

## [2.2.5]

### Fixed
- Remove line terminator after escape in string literals

## [2.2.4]

### Fixed
- Fixed the way quote characters are counted in string literals [SourceForge bug #1804576]
- Do not use a regular expression using non-greedy matching to remove CSS comments (if the comment is more than 800 characters long or so, a stack overflow exception gets thrown) Instead, use good old parsing...
- Fix unnecessary quote escaping in string literals

## [2.2.3]

### Added
- Added --preserve-strings option to specify that concatenated string literals should never be merged

### Fixed
- Transform </script into <\/script instead of replacing all </ into <\/.
- Fixed bug related to the shortening of hexadecimal color codes (the string "1px solid #aabbcc" became "1px solid#abc", missing a required white space)

### Changed
- Do not convert \uXXXX and \xXX escape sequences to their unicode equivalent

## [2.2.2]

### Added
- Modified the Rhino tokenizer to handle JScript conditional comments natively (instead of hacking around the fact that Rhino is not keeping track of comments)
- Transform </ into <\/ in string literals. This is especially useful if the code is written to a script block in an HTML document. This renders the old hack '<scr'+'ipt ...><'+'/script>' completely useless

### Fixed
- Fixed regression related to the optimization of the amount of escaping in string literals and the concatenation of string literals
- When converting decimal rgb color values to hexadecimal color values, prepend a '0' if the value is less than 16. Otherwise, rgb(0,124,114) for instance becomes #07c72, which is incorrect
- In CSS files, do not change color names into their corresponding color codes (and vice-versa) due to the high potential of introducing bugs (rolled back from 2.2.1)

## [2.2.1]

### Added
- Optimize quote escaping in JavaScript string literals by using the best quote character (' or " depending on the occurrence of this character in the string)

### Fixed
- Fixed minor bug in the CSS compressor. Colors should not be shortened in `filter: chroma(color="#FFFFFF");` Otherwise, it makes the filter break in Internet Explorer

### Changed
- In CSS files, change color names into their corresponding color codes (and vice-versa) if that change yields any savings

## [2.2]

### Added
- Added support for stdin/stdout (see README for more info)
- Added support for Internet Explorer's conditional comments in JavaScript files. Note that the presence of a conditional comment inside a function (i.e. not in the global scope) will reduce the level of compression for the same reason the use of 'eval' or 'with' reduces the level of compression (conditional comments, which do not get parsed, may refer to local variables, which get obfuscated) In any case, the use of Internet Explorer's conditional comment is to be avoided

### Improved
- Shorten colors from rgb(51,102,153) to #336699 in CSS files
- Shorten values from 0.8em to .8em in CSS files

### Changed
- Don't obfuscate function argument named $super if it is the first function argument listed. This is to support Prototype 1.6's heretic implementation

## [2.1.2]

### Added
- Added --preserve-semi option
- Modified --line-break option

## [2.1.1]

### Fixed
- Fixed missing space in CSS background:url('foo.png')no-repeat causing a background not to appear on Internet Explorer

## [2.1]

### Added
- Pass the --line-break option to the CSS compressor
- Allow the output file to overwrite the input file (with version 2.0, in this case, the output file was always empty)
- Merge (if possible) string literals that are appended in JavaScript files. This not only makes the code smaller, it makes the code faster, but allows you to maintain some readability in your source code
- Pass ErrorReporter instance to the constructor of class JavaScriptCompressor (as suggested by David Bernard for his integration of the YUI Compressor as a maven plugin)

### Improved
- Remove spaces before and after '(' and ')' as in background:url('xxx');

### Fixed
- Handle constructs such as a + ++ b or a + + "1" (in which case the space between the operators must be kept!) and other similar cases...

## [2.0]

### Added
- Integrated Isaac Schlueter's CSS compressor
- Output a white-space character after 'break' and 'continue' when followed by a label

### Changed
- Switched from Rhino 1.6R6 to Rhino 1.6R7
- Refactored code to make it easier to use the compressor from a servlet environment or another Java app (no need to pass in file names anymore)

### Improved
- Output a white-space character after 'throw' only when necessary

## [1.1]

### Added
- Added --line-break option that adds a line feed character after each semi-colon character (may help debugging with the MS Script debugger)
- Added support for missing JavaScript features (get, set, const)
- Added web-based front-end to the YUI Compressor as part of the dist package
- Added a public entry point that makes the YUI Compressor easy to integrate with an already existing Java application
- Count how many times each identifier is used, and display a warning when an identifier seems to be unused (code cannot safely be removed automatically)

### Changed
- Java source now in package com.yahoo.platform.yui.compressor
- Simplified code by using the same parsing routines used to build the symbol tree while looking for undeclared symbols

### Fixed
- Do not show the entire stack trace when the input file cannot be found

### Improved
- Removed the randomization of obfuscated symbols. When compressed code is checked in CVS, unchanged files would otherwise end up being versioned
- Remove ';' when followed by a '}'. This yields an additional ~1.5% savings on yahoo-dom-event.js compared to the JSMin version
- Output a white-space character after 'return' and 'case' only when necessary
