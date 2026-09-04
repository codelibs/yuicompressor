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
- At-rules and declarations are now only recognised where one can actually begin - after `}`,
  `;`, `{`, a preserved-token placeholder, or the start of the stylesheet. Matching their literal
  text anywhere it appeared meant `a { background: url(/img/@property.png) }` was treated as an
  `@property` block and left the *following* rule entirely unminified; `url(/img/@MEDIA.png)` was
  lowercased even though URL paths are case-sensitive; and an `@charset "x";` inside an
  unpreserved `url()` was hoisted out of the URL to the top of the stylesheet
- Empty `@layer` blocks (e.g. `@layer base, components, utilities;`) are no longer deleted; they
  declare cascade order even with no rules inside
- Modern at-rule names (`@container`, `@layer`, `@property`, `@scope`,
  `@starting-style`, `@supports`) are now normalised to lowercase, extending the
  existing at-rule lowercasing list

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
  (`-Dyuicompressor.strict`) makes any *future* unhandled node type throw instead of degrading
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
  `{b:a}` when the binding is munged, and stays shorthand when it is not
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

### Improved
- Function expressions passed as call arguments, object property values, and array elements now
  get scopes and have their parameters munged, closing a gap in `ScopeBuilder`'s traversal;
  jQuery 1.6.4 minified output went from 137,798 to 106,970 bytes
- Redundant brace pairs are gone. Rhino wraps a loop, if- or do-body that declares anything in a
  `Scope`, which does not extend `Block`, so an `instanceof Block` check missed it and wrapped an
  already-braced block in a second pair: `for(...){f();}` came out as `for(...){{f();}}`. jQuery
  1.6.4 measured 106,970 -> 104,770 bytes, with the `{{` count going 1,100 -> 0
- `ScopeBuilder` now traverses default parameter expressions, which Rhino keeps in a side list
  rather than as children. A name read there is a real use, and an `eval` there is a real reason
  not to munge

### Documented (behaviour that was described inaccurately)
- `--preserve-semi`, `--disable-optimizations` and `-v/--verbose` are accepted and **ignored**.
  They are now marked as such in the README and in the `compress(...)` javadoc. Implementing them
  is Release 2 work; the Release 1 fix is that a caller passing them is no longer left believing
  they took effect
- `"name:nomunge"` hints are **not implemented**: the named symbols are munged anyway and the hint
  string is emitted into the output as a live statement rather than disappearing. The README said
  otherwise
- `--line-break 0` gives a line break after each rule in CSS, but is a **no-op in JavaScript**.
  The README described the JavaScript behaviour that has never existed

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
  guard (`JsOutputSyntaxTest`) that runs `node --check` against all 10 JS fixtures plus a
  comment-injection scanner over every one of them
- Added `DifferentialExecutionTest`: 25 small, deterministic scripts run under node twice, once
  as source and once compressed, comparing stdout and exit status. `node --check` only proves the
  output parses, and *every* silent-corruption defect in this release produced output that
  parsed. This test found one nobody had reported - the shorthand-property key renaming above
- Test suite grew from 163 to 457 tests (2 skipped: the two `ES6SupportTest` cases Rhino cannot
  parse). Without node on `PATH`, 422 execute and 3 further test methods are skipped
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
