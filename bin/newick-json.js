#!/usr/bin/env node
'use strict'

const getData = require('../lib/get-data')
const newick = require('../vendor/newick').parse

function main() {
	let file = process.argv[2]

	if (!file && process.stdin.isTTY) {
		console.error('usage: node newick-json.js (<input> | -)')
		process.exit(1)
	}

	return getData(file)
		.then(newick)
		.then(d => JSON.stringify(d))
		.then(data => console.log(data))
		.catch(console.error.bind(console))
}

if (require.main === module) {
	main()
}
