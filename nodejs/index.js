
var spawn = require('child_process').spawn,
    fs = require('fs'),
    path = require('path'),
    jar,
    exists = fs.exists || path.exists;

// Find JAR in Maven target directory
var searchPaths = [
    path.join(__dirname, '../target')
];

for (var i = 0; i < searchPaths.length; i++) {
    var searchPath = searchPaths[i];
    if (fs.existsSync(searchPath)) {
        var lists = fs.readdirSync(searchPath);
        var found = lists.some(function(item) {
            // Only the shaded jar can be executed: maven-shade-plugin leaves the
            // pre-shade jar beside it as original-yuicompressor-<version>.jar,
            // which carries no dependencies and dies with NoClassDefFoundError
            // on args4j, and maven-jar-plugin can add -sources/-javadoc jars to
            // the same directory. Matching 'yuicompressor' anywhere in the name
            // picked whichever the filesystem happened to list first.
            if (/^yuicompressor-.*\.jar$/.test(item) && !/-(sources|javadoc)\.jar$/.test(item)) {
                jar = path.join(searchPath, item);
                return true;
            }
        });
        if (found) break;
    }
}

if (!jar) {
    throw new Error('YUI Compressor JAR file not found. Please build the project first using "mvn package".');
}

exports.jar = jar;

var defaultOptions = {
    charset: 'utf8',
    type: 'js'
};

var validOptions = {
    charset: 1,
    type: 1,
    'line-break': 1,
    nomunge: 1,
    'preserve-semi': 1,
    'disable-optimizations': 1
};

var getString = function(str, callback, options) {
    exists(str, function(y) {
        if (y) {
            var ext = (path.extname(str)).replace('.', '');
            fs.readFile(str, 'utf8', function(err, data) {
                //Set the type from the file name
                options.type = ext;
                callback(err, data, options);
            });
        } else {
            callback(null, str, options);
        }
    });
};


var filterOptions = function(options) {
    Object.keys(options).forEach(function(key) {
        if (!validOptions[key]) {
            delete options[key];
        }
    });

    options.type = options.type || 'js';
    options.charset = options.charset || 'utf8';
    return options;
};

var compressString = function(str, options, callback) {
    //Now we have a string, spawn and pipe it in.
    
    options = filterOptions(options);

    var args = [
        '-jar',
        exports.jar
    ], buffer = '', errBuffer = '', stdinError = null, settled = false, child;

    // Exactly one of the child's outcomes reaches the caller. Without this a
    // process that both fails to spawn and emits a broken pipe would call back
    // twice.
    var settle = function(err) {
        if (settled) {
            return;
        }
        settled = true;
        callback(err, buffer, errBuffer);
    };

    Object.keys(options).forEach(function(key) {
        args.push('--' + key);
        if (options[key] && options[key] !== true) {
            args.push(options[key]);
        }
    });

    child = spawn('java', args, {
        stdio: ['pipe', 'pipe', 'pipe']
    });

    // No java on PATH emits 'error' and never emits 'exit', so without this
    // handler the callback was never invoked at all and the error was thrown
    // as an uncaught exception.
    child.on('error', function(e) {
        settle(e);
    });

    // Writing to a child that has already died fails here. The exit or error
    // handler is the one that explains why, so record it and let them report;
    // it is only surfaced below if the child somehow still exits cleanly.
    child.stdin.on('error', function(e) {
        stdinError = e;
    });

    child.stdin.write(str);
    child.stdin.end();
    
    child.stdout.on('data', function(chunk) {
        buffer += chunk;
    });
    child.stderr.on('data', function(chunk) {
        errBuffer += chunk;
    });
    
    child.on('exit', function(code) {
        var err = null;
        // A non-zero exit is a failure whether or not the compressor got far
        // enough to print its own '[ERROR]' marker. Anything that kills the
        // JVM first - a missing class, an unreadable jar - used to arrive here
        // as success with an empty string for the compressed output.
        if (code !== 0) {
            err = errBuffer || new Error('java exited with code ' + code);
        } else if (errBuffer.indexOf('[ERROR]') > -1) {
            err = errBuffer;
        } else if (stdinError) {
            err = stdinError;
        }
        settle(err);
    });
};

var compress = function(str, options, callback) {
    if (typeof options === 'function') {
        callback = options;
        options = defaultOptions;
    }

    getString(str, function(err, str, options) {

        // A file that exists but cannot be read left `str` undefined, and the
        // error was dropped here rather than handed to the caller.
        if (err) {
            callback(err, '', '');
            return;
        }

        compressString(str, options, callback);
        
    }, options);
};

exports.compress = compress;
exports.compressString = compressString;
