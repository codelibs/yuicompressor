package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.yahoo.platform.yui.compressor.CssCompressor;

/**
 * Compresses every *.css fixture that has a matching *.css.min golden file and
 * compares the result. These fixtures existed in the repository but were never
 * executed by any test.
 */
class CssGoldenFileTest {

    private static final Path RESOURCES = Paths.get("src/test/resources");

    /**
     * Fixtures whose golden file does not match current output.
     *
     * zeros.css is quarantined deliberately and permanently. Its golden expects
     * "transition-delay:0" from "0.0ms", but CSS Values and Units Level 3 allows
     * omitting the unit only for a zero <length>; <time> has no such exemption.
     * Matching this golden would make the compressor emit invalid CSS, so the
     * golden is kept as a record of upstream YUI's behaviour and is not matched.
     * See ModernCssTest.zeroTimeValuesKeepTheirUnit.
     */
    private static final List<String> KNOWN_FAILURES = List.of("zeros.css");

    static Stream<String> fixtures() throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".css"))
                    .filter(n -> !n.startsWith("_"))
                    .filter(n -> Files.exists(RESOURCES.resolve(n + ".min")))
                    .filter(n -> !KNOWN_FAILURES.contains(n))
                    .sorted()
                    .collect(Collectors.toList())
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void compressesToGoldenFile(String fixture) throws Exception {
        String source = new String(Files.readAllBytes(RESOURCES.resolve(fixture)), StandardCharsets.UTF_8);
        String expected = new String(Files.readAllBytes(RESOURCES.resolve(fixture + ".min")), StandardCharsets.UTF_8);

        StringWriter out = new StringWriter();
        new CssCompressor(new StringReader(source)).compress(out, -1);

        assertEquals(expected.trim(), out.toString().trim(), "golden mismatch: " + fixture);
    }
}
