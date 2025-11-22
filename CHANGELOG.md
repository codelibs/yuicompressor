# Changelog

All notable changes to YUI Compressor will be documented in this file.

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
