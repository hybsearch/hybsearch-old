#!/usr/bin/env node
'use strict'

require('./vendor/array.proto.includes')
const getData = require('./bin/lib_get-data')
const combs = require('combinations-generator')
const uniqBy = require('lodash/uniqBy')

const ENABLE_DEBUG = false
let debug = ENABLE_DEBUG && console.log.bind(console) || () => {}

let pluck = (arr, key) => arr.map(x => x[key])
let label = node => `${node.name}-${node.ident}`

function nmMark(node, species1, species2) {
	if (node.branchset) {
		nmMark(node.branchset[0], species1, species2)
		nmMark(node.branchset[1], species1, species2)
	}
	else if (node.name === species1.name) {
		node.nm_inner = node.nm_inner || []
		node.nm_inner.push(species2)
	}
	else if (node.name === species2.name) {
		node.nm_outer = node.nm_outer || []
		node.nm_outer.push(species1)
	}
}

function nmSearch(node) {
	let name = node.name
	if (name && !node.ident) {
		// console.log(name)
		node.ident = name.split('-')[1]
		node.name = name.split('-')[0]
	}

	if (node.branchset) {
		debug('has branchset')
		let combinations = combs(node.branchset, 2)

		let speciesList = []
		for (let speciesSet of combinations) {
			let speciesListA = nmSearch(speciesSet[0])
			let speciesListB = nmSearch(speciesSet[1])

			debug('speciesListA:', speciesListA, 'speciesListB', speciesListB)

			let speciesBNames = pluck(speciesListB, 'name')
			speciesListA.forEach(species1 => {
				let hasName = speciesBNames.includes(species1.name)
				let notAllEqual = !(speciesBNames.every(n => n === species1))
				debug(`included: ${hasName}; not all equal: ${notAllEqual}`)

				// species1 is in speciesListB, and not everything in speciesListB is species1
				if (hasName && notAllEqual) {
					// search in speciesListB
					speciesListB.forEach(species2 => {
						if (species2.name !== species1.name) {
							nmMark(node, species1, species2)
							console.log(`nonmonophyly: ${label(species1)} / ${label(species2)}`)
							debug(`nmMark called on ${species1} and ${species2}`)
						}
					})

					// and search in speciesListA
					speciesListA.forEach(species3 => {
						if (species3.name !== species1.name) {
							nmMark(node, species1, species3)
							console.log(`nonmonophyly: ${label(species1)} / ${label(species3)}`)
							debug(`nmMark called on ${species1} and ${species3}`)
						}
					})
				}
			})

			speciesList = speciesList.concat(speciesListA, speciesListB)
		}

		speciesList = uniqBy(speciesList, 'ident')
		debug('speciesList', speciesList)
		return speciesList
	}

	debug(`no branchset, name: ${node.name}, ident: ${node.ident}`)
	return [node]
}



function main() {
	let file = process.argv[2]

	if (!file && process.stdin.isTTY) {
		console.error('usage: node ent.js (<input> | -)')
		process.exit(1)
	}

	getData(file)
		.then(d => JSON.parse(d))
		.then(nmSearch)
		.catch(console.error.bind(console))
}

if (require.main === module) {
	main()
}