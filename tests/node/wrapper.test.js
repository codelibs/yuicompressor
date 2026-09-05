'use strict';

/**
 * Tests for the Node.js wrapper in nodejs/index.js.
 *
 * The wrapper is a thin shell around `java -jar`, and every defect it has had
 * so far has been in that shell rather than in the compressor: which jar it
 * picks out of target/, and whether a failure of the child process reaches the
 * caller. Those are what these tests cover. The compression itself is already
 * pinned by the Java suite, so the expected strings here are deliberately
 * small - enough to prove the right jar ran, not a second golden corpus.
 *
 * Requires `mvn package` to have produced target/yuicompressor-*.jar.
 */

const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const compressor = require('../../nodejs/index');

const ROOT = path.join(__dirname, '..', '..');

test('picks the shaded jar, not a side artifact', () => {
    const name = path.basename(compressor.jar);

    // maven-shade-plugin leaves the pre-shade jar next to the shaded one as
    // original-yuicompressor-<version>.jar. It carries no dependencies, so
    // running it dies with NoClassDefFoundError on args4j. maven-jar-plugin
    // can add -sources and -javadoc jars to the same directory.
    assert.ok(!name.startsWith('original-'),
        `selected the pre-shade jar: ${name}`);
    assert.ok(!/-(sources|javadoc)\.jar$/.test(name),
        `selected a non-executable side artifact: ${name}`);
    assert.match(name, /^yuicompressor-.*\.jar$/);
});

test('compresses JavaScript', (t, done) => {
    compressor.compress(path.join(ROOT, 'tests', 'node', 'files', 'wrapper.js'),
        { type: 'js', charset: 'utf8' }, (err, out) => {
            assert.ifError(err);
            assert.strictEqual(out.trim(), 'function hello(a){var b="hi, ";return b+a}');
            done();
        });
});

test('compresses CSS', (t, done) => {
    compressor.compress(path.join(ROOT, 'tests', 'node', 'files', 'wrapper.css'),
        { type: 'css', charset: 'utf8' }, (err, out) => {
            assert.ifError(err);
            assert.strictEqual(out.trim(), 'a{color:#fff;margin:0}');
            done();
        });
});

test('compresses a string that is not a path', (t, done) => {
    compressor.compressString('var x = 1;', { type: 'js', charset: 'utf8' }, (err, out) => {
        assert.ifError(err);
        assert.strictEqual(out.trim(), 'var x=1;');
        done();
    });
});

/*
 * Failure reporting.
 *
 * A wrapper that cannot report a failure turns every failure into an empty
 * file. `err` was derived solely from the substring '[ERROR]' appearing in
 * stderr, so anything that killed the JVM before the compressor could print
 * that marker - a missing class, a missing jar, a missing java - came back as
 * `err === null` with an empty string as the compressed output.
 */

const NO_MARKER_IN_STDERR = /\[ERROR\]/;

test('reports a non-zero exit even when stderr carries no [ERROR] marker', (t, done) => {
    const realJar = compressor.jar;
    compressor.jar = path.join(ROOT, 'target', 'does-not-exist.jar');

    compressor.compressString('var x = 1;', { type: 'js' }, (err, out, stderr) => {
        compressor.jar = realJar;
        assert.doesNotMatch(String(stderr), NO_MARKER_IN_STDERR,
            'this case is only meaningful while stderr has no [ERROR] marker');
        assert.ok(err, 'a java process that exits non-zero must be reported as an error');
        assert.strictEqual(out, '');
        done();
    });
});

test('reports a spawn failure instead of hanging', { timeout: 15000 }, (t, done) => {
    const realPath = process.env.PATH;
    process.env.PATH = '';

    compressor.compressString('var x = 1;', { type: 'js' }, (err) => {
        process.env.PATH = realPath;
        assert.ok(err, 'java missing from PATH must be reported as an error');
        done();
    });
});

test('reports a file that cannot be read', (t, done) => {
    // A directory exists, so the wrapper takes it for an input path, and then
    // fs.readFile fails with EISDIR.
    compressor.compress(path.join(ROOT, 'tests', 'node', 'files'), { type: 'js' }, (err) => {
        assert.ok(err, 'a read error must reach the caller');
        done();
    });
});
