# YUI Compressor Tools

This directory contains debugging and development tools for YUI Compressor.

## CSS Minifier Debugger

**File**: `cssmin-debugger.html`

A browser-based debugging tool for the CSS minifier. This tool allows you to:

- Load local CSS files through your browser
- Step through the CSS minification process using browser developer tools
- View the original and compressed CSS side-by-side
- Debug issues with the JavaScript port of the CSS minifier

### How to Use

1. Open `cssmin-debugger.html` in a modern web browser (requires File API support)
2. Click "Choose File" and select a CSS file from your computer
3. Open your browser's developer tools (F12)
4. Set breakpoints in `cssmin.js` to debug the minification process
5. View the results in the page

### Requirements

- Modern browser with File API support (Chrome, Firefox, Safari, Edge)
- The file references `../ports/js/cssmin.js` - ensure this path is correct

### Technical Details

The debugger:
- Uses the File API to read local files
- Processes CSS through the `YAHOO.compressor.cssmin()` function
- Displays both original and compressed output
- Supports UTF-8 encoding

## Future Tools

Additional development and debugging tools can be placed in this directory.
