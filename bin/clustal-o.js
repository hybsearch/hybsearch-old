#!/usr/bin/env node
'use strict'

const child = require('child_process')
const tempfile = require('tempfile')
const fs = require('fs')
const getData = require('./lib_get-data')
const minimist = require('minimist')

module.exports = clustal
function clustal(data) {
	const inputFile = tempfile().replace(' ', '\ ')
	const outputFile = tempfile().replace(' ', '\ ')
	fs.writeFileSync(inputFile, data, 'utf-8')

	const argString = `clustalo --in ${inputFile} --out ${outputFile} --outfmt=fasta`

	child.execSync(argString)

	return fs.readFileSync(outputFile, 'utf-8')
}

function main() {
	let argv = minimist(process.argv.slice(2))
	let file = argv['_'][0]

	if (!file && process.stdin.isTTY) {
		console.error('usage: node clustal-o.js (<input> | -) [output]')
		process.exit(1)
	}

	getData(file)
		.then(clustal)
		.then(output => {
			if (argv['_'][1] === 2) {
				fs.writeFileSync(argv['_'][1], output, 'utf-8')
			} else {
				console.log(output)
			}
		})
		.catch(console.error.bind(console))
}

if (require.main === module) {
	main()
}