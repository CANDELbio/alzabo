# alzabo

Alzabo does a number of different tasks centered around a simple schema format for graph databases.

- Defines an .edn schema format, with semantics similar to RDF.
- Tool to convert the [CANDEL schema](https://github.com/ParkerICI/pret/tree/master/resources/schema) into Alzabo format
- Tool to generate Datomic schemas from Alzabo format
- Tool to generate HTML documentation from Alzabo schemas
- A Clojurescript applet to do autocompletion over Alzabo schemas (appears as part of HTML doc)

## Schema format

Schemas are represented as EDN maps. See [an example](test/resources/schema/rawsugar.edn) or the [schema spec](src/cljc/org/parkerici/alzabo/schema.cljc).

`:title` a string
`:version` a string
`:kinds` a map of kind names (keywords) to kind definitions (see below)
`:enums` A map of enum names (keywords) to sequence of enum values (also keywords, generally namespaced)

A kind definition is a map with attributes:
`:fields`: a map of field names (keywords) to field definitions
`:doc` a string
`:reference?` a boolean indicating that this is a reference class (TODO rather too CANDEL specific, maybe generalize)

A field definition is a map with attributes:
`:type` can be:
 - a keyword, either a kind name, a primitive
 - a vector of types (defines a Datomic heterogenous tuple)
 - a map of the form `{:* <type>}` (defines a Datomic homogenous tuple)
   Default is `:string`
`:doc` a string
`:cardinality` Either `:one` (default) or `:many`
`:unique` Either `:identity` or `:value`, see [Datomic doc](https://docs.datomic.com/on-prem/schema.html#operational-schema-attributes) for details.
`:unique-id` (deprecated) `true` means the same as `:unique :identity`
 `:attribute` the datomic or sparql attribute corresponding to the field 

The defined primitives are `#{:string :boolean :float :double :long :bigint :bigdec :instant :keyword :uuid}`. 

## Installation

To generate documentation, you need graphviz installed. On the Mac, you can do this with

    $ brew install graphviz


## Usage

### Generate and view CANDEL schema

Prerequisites:
- [leiningen](https://leiningen.org/)
- [Pret](https://github.com/CANDELbio/pret) installed as a sibling to Alzabo (TODO update to point to opensource version)

    $ lein launch

Will compile the CANDEL schema to static files, compile the clojurescript code for autocomplete, and open the schema web page. 

Or, to use a locally (but possibly out of date) version of the schema:

    lein run test/resources/test-config.edn datomic
    lein run test/resources/test-config.edn documentation
    lein run test/resources/test-config.edn server 

## Commands

You can run these commands with `lein run <config> <cmd>`. 

	$ lein run documentation 
	
Generates documentation from the given Alzabo schema file. 

	$ lein run datomic 
	
Generates a Datomic schema from the given Alzabo schema file. 

	$ lein run server

Opens the generated documentation in browser..

## Use as a library

Add dependency `[org.parkerici/alzabo "1.0.0"]` (or whatever the lastest version is)

### Example

    (ns ...
	  (:require [org.parkerici.alzabo.schema :as schema]
                [org.parkerici.alzabo.datomic :as datomic]
				[org.parkerici.alzabo.html :as html]))

	;; read in a schema file
	(let [schema (schema/read-schema <schema.edn>)]

	  ;; write out a Datomic schema
      (datomic/write-schema schema "datomic-schema.edn")
	
      ;; generate documentation 
      (html/schema->html schema "public/schema" {}))


