/*
 * YUI Compressor
 * http://developer.yahoo.com/yui/compressor/
 * Author: Julien Lecomte -  http://www.julienlecomte.net/
 * Copyright (c) 2011 Yahoo! Inc.  All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */
package com.yahoo.platform.yui.compressor;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class YUICompressor {

    @Option(name = "-V", aliases = {"--version"}, usage = "Print version information")
    private boolean showVersion = false;

    @Option(name = "-h", aliases = {"--help"}, usage = "Displays this information")
    private boolean showHelp = false;

    @Option(name = "--type", usage = "Specifies the type of the input file (js or css)")
    private String type = null;

    @Option(name = "--charset", usage = "Read the input file using specified charset")
    private String charset = null;

    @Option(name = "--line-break", metaVar = "COLUMN", usage = "Insert a line break after the specified column number")
    private String lineBreak = null;

    @Option(name = "-v", aliases = {"--verbose"}, usage = "Display informational messages and warnings")
    private boolean verbose = false;

    @Option(name = "-p", aliases = {"--preservehints"}, usage = "Don't elide unrecognized compiler hints")
    private boolean preserveHints = false;

    @Option(name = "-m", metaVar = "FILE", usage = "Place a mapping of munged identifiers to originals in this file")
    private String mungemapFile = null;

    @Option(name = "-o", metaVar = "FILE", usage = "Place the output into specified file")
    private String outputFile = null;

    @Option(name = "--nomunge", usage = "Minify only, do not obfuscate")
    private boolean nomunge = false;

    @Option(name = "--preserve-semi", usage = "Preserve all semicolons")
    private boolean preserveSemi = false;

    @Option(name = "--disable-optimizations", usage = "Disable all micro optimizations")
    private boolean disableOptimizations = false;

    @Argument(metaVar = "INPUT_FILES", usage = "Input files to compress")
    private List<String> inputFiles = new ArrayList<>();

    public static void main(String[] args) {
        YUICompressor compressor = new YUICompressor();
        CmdLineParser parser = new CmdLineParser(compressor);

        try {
            parser.parseArgument(args);
            compressor.run();
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            compressor.usage(parser);
            System.exit(1);
        }
    }

    private void run() {
        if (showHelp) {
            usage(null);
            System.exit(0);
        }

        if (showVersion) {
            version();
            System.exit(0);
        }

        Reader in = null;
        Writer out = null;
        Writer mungemap = null;

        try {
            // Validate and set default charset
            if (charset == null || !Charset.isSupported(charset)) {
                charset = "UTF-8";
                if (verbose) {
                    System.err.println("\n[INFO] Using charset " + charset);
                }
            }

            // Parse line break position
            int linebreakpos = -1;
            if (lineBreak != null) {
                try {
                    linebreakpos = Integer.parseInt(lineBreak, 10);
                } catch (NumberFormatException e) {
                    usage(null);
                    System.exit(1);
                }
            }

            // Validate type if specified
            if (type != null && !type.equalsIgnoreCase("js") && !type.equalsIgnoreCase("css")) {
                usage(null);
                System.exit(1);
            }

            // Determine munge setting (nomunge inverts the logic)
            boolean munge = !nomunge;

            // Handle empty input files (use stdin)
            List<String> files = inputFiles;
            if (files.isEmpty()) {
                if (type == null) {
                    usage(null);
                    System.exit(1);
                }
                files = new ArrayList<>();
                files.add("-"); // read from stdin
            }

            // Parse output pattern
            String[] pattern;
            if (outputFile == null) {
                pattern = new String[0];
            } else if (outputFile.matches("(?i)^[a-z]\\:\\\\.*")) {
                // Windows path (e.g., C:\path)
                pattern = new String[]{outputFile};
            } else {
                pattern = outputFile.split(":");
            }

            // Open mungemap file if specified
            try {
                if (mungemapFile != null) {
                    mungemap = new OutputStreamWriter(new FileOutputStream(mungemapFile), charset);
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }

            // Process each input file
            for (String inputFilename : files) {
                String fileType = null;
                try {
                    if (inputFilename.equals("-")) {
                        in = new InputStreamReader(System.in, charset);
                        fileType = type;
                    } else {
                        if (type != null) {
                            fileType = type;
                        } else {
                            int idx = inputFilename.lastIndexOf('.');
                            if (idx >= 0 && idx < inputFilename.length() - 1) {
                                fileType = inputFilename.substring(idx + 1);
                            }
                        }

                        if (fileType == null || !fileType.equalsIgnoreCase("js") && !fileType.equalsIgnoreCase("css")) {
                            usage(null);
                            System.exit(1);
                        }

                        in = new InputStreamReader(new FileInputStream(inputFilename), charset);
                    }

                    String outputFilename = outputFile;
                    // Apply substitution pattern if provided
                    if (pattern.length > 1 && files.size() > 0) {
                        outputFilename = inputFilename.replaceFirst(pattern[0], pattern[1]);
                    }

                    if (fileType.equalsIgnoreCase("js")) {
                        try {
                            final String localFilename = inputFilename;

                            JavaScriptCompressor compressor = new JavaScriptCompressor(in, new ErrorReporter() {
                                public void warning(String message, String sourceName,
                                        int line, String lineSource, int lineOffset) {
                                    System.err.println("\n[WARNING] in " + localFilename);
                                    if (line < 0) {
                                        System.err.println("  " + message);
                                    } else {
                                        System.err.println("  " + line + ':' + lineOffset + ':' + message);
                                    }
                                }

                                public void error(String message, String sourceName,
                                        int line, String lineSource, int lineOffset) {
                                    System.err.println("[ERROR] in " + localFilename);
                                    if (line < 0) {
                                        System.err.println("  " + message);
                                    } else {
                                        System.err.println("  " + line + ':' + lineOffset + ':' + message);
                                    }
                                }

                                public EvaluatorException runtimeError(String message, String sourceName,
                                        int line, String lineSource, int lineOffset) {
                                    error(message, sourceName, line, lineSource, lineOffset);
                                    return new EvaluatorException(message);
                                }
                            });

                            // Close input stream before opening output stream
                            in.close();
                            in = null;

                            if (outputFilename == null) {
                                out = new OutputStreamWriter(System.out, charset);
                            } else {
                                out = new OutputStreamWriter(new FileOutputStream(outputFilename), charset);
                                if (mungemap != null) {
                                    mungemap.write("\n\nFile: " + outputFilename + "\n\n");
                                }
                            }

                            compressor.compress(out, mungemap, linebreakpos, munge, verbose,
                                    preserveSemi, disableOptimizations, preserveHints);

                        } catch (EvaluatorException e) {
                            e.printStackTrace();
                            // Return a special error code used specifically by the web front-end
                            System.exit(2);
                        }

                    } else if (fileType.equalsIgnoreCase("css")) {
                        CssCompressor compressor = new CssCompressor(in);

                        // Close input stream before opening output stream
                        in.close();
                        in = null;

                        if (outputFilename == null) {
                            out = new OutputStreamWriter(System.out, charset);
                        } else {
                            out = new OutputStreamWriter(new FileOutputStream(outputFilename), charset);
                        }

                        compressor.compress(out, linebreakpos);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } finally {
            if (mungemap != null) {
                try {
                    mungemap.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void version() {
        System.err.println("@VERSION@");
    }

    private void usage(CmdLineParser parser) {
        System.err.println(
                "YUICompressor Version: @VERSION@\n"

                        + "\nUsage: java -jar yuicompressor-@VERSION@.jar [options] [input file]\n"
                        + "\n"
                        + "Global Options\n"
                        + "  -V, --version             Print version information\n"
                        + "  -h, --help                Displays this information\n"
                        + "  --type <js|css>           Specifies the type of the input file\n"
                        + "  --charset <charset>       Read the input file using <charset>\n"
                        + "  --line-break <column>     Insert a line break after the specified column number\n"
                        + "  -v, --verbose             Display informational messages and warnings\n"
                        + "  -p, --preservehints       Don't elide unrecognized compiler hints (e.g. \"use strict\", \"use asm\")\n"
                        + "  -m <file>                 Place a mapping of munged identifiers to originals in this file\n\n"
                        + "  -o <file>                 Place the output into <file>. Defaults to stdout.\n"
                        + "                            Multiple files can be processed using the following syntax:\n"
                        + "                            java -jar yuicompressor.jar -o '.css$:-min.css' *.css\n"
                        + "                            java -jar yuicompressor.jar -o '.js$:-min.js' *.js\n\n"

                        + "JavaScript Options\n"
                        + "  --nomunge                 Minify only, do not obfuscate\n"
                        + "  --preserve-semi           Preserve all semicolons\n"
                        + "  --disable-optimizations   Disable all micro optimizations\n\n"

                        + "If no input file is specified, it defaults to stdin. In this case, the 'type'\n"
                        + "option is required. Otherwise, the 'type' option is required only if the input\n"
                        + "file extension is neither 'js' nor 'css'.");
    }
}
