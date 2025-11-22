#!/usr/bin/env node

/*
Just a simple nodejs wrapper around the .jar file
for easy CLI use
*/

var spawn = require('child_process').spawn,
    fs = require('fs'),
    compressor = require('./index'),
    args = process.argv.slice(2);

args.unshift(compressor.jar);
args.unshift('-jar');

var child = spawn('java', args, { stdio: 'inherit' });
child.on('exit', function(code) {
    process.exit(code);
});
