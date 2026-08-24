# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Building and Running
- `lein launch` - Full build pipeline: clean, run datomic and documentation generation, build ClojureScript, start server
- `lein run <config-file> <command>` - Run specific commands with configuration
  - `lein run resources/candel-config.edn datomic` - Generate Datomic schema
  - `lein run resources/candel-config.edn documentation` - Generate HTML documentation
  - `lein run resources/candel-config.edn server` - Start documentation server and open in browser
  - `lein run test/resources/test-config.edn <command>` - Use test configuration

### Testing
- `lein test` - Run Clojure tests
- Test files are in `test/clj/`, `test/cljc/`, and `test/cljs/` directories

### ClojureScript Development
- `lein figwheel` - Start Figwheel for interactive ClojureScript development
- `lein cljsbuild once` - Build ClojureScript once
- `lein cljsbuild auto` - Build ClojureScript automatically on changes
- Development server runs on port 3452 with nREPL on port 7888

### Library Installation
- `lein with-profile library,prod install` - Install as local library

## Code Architecture

### Core Components
- **Schema Management**: `src/cljc/org/candelbio/alzabo/schema.cljc` - Core schema format definition and validation using clojure.spec
- **Configuration**: `src/clj/org/candelbio/alzabo/config.clj` - Configuration management using Aero, supports templating and environment-specific configs
- **CLI Interface**: `src/clj/org/candelbio/alzabo/core.clj` - Main entry point with multimethod-based command dispatch

### Key Modules
- **HTML Generation**: `src/clj/org/candelbio/alzabo/html.clj` - Converts schemas to HTML documentation with visualization
- **Datomic Export**: `src/clj/org/candelbio/alzabo/datomic.clj` - Converts Alzabo schemas to Datomic format
- **OpenAPI Import**: `src/clj/org/candelbio/alzabo/openapi.clj` - Imports OpenAPI YAML schemas
- **CANDEL Integration**: `src/clj/org/candelbio/alzabo/candel.clj` - Specific integration for CANDEL schema format
- **GraphQL**: `src/clj/org/candelbio/alzabo/graphql.clj` and `graphql_gen.clj` - GraphQL schema generation
- **Search UI**: `src/cljs/org/candelbio/alzabo/search/core.cljs` - ClojureScript search interface for generated documentation

### Schema Format
Alzabo uses EDN-based schema definitions with:
- `:kinds` - Entity type definitions (similar to classes)
- `:fields` - Field definitions with type, cardinality, and documentation
- `:enums` - Enumeration definitions
- Primitive types: `#{:string :boolean :float :double :long :bigint :bigdec :instant :keyword :uuid}`
- Support for tuples (heterogeneous and homogeneous)
- RDF-like semantics for graph database mapping

### Configuration System
- Uses Aero for environment-aware configuration
- Template variable substitution with `{{variable}}` syntax
- Config files in `resources/` for different environments (candel, test, etc.)
- Default config embedded in `config.clj`

### Multi-Platform Support
- **Clojure** (`src/clj/`): Server-side schema processing, HTML generation, CLI
- **ClojureScript** (`src/cljs/`): Browser-based search and UI components
- **Cross-platform** (`src/cljc/`): Shared schema definitions and utilities

### Output Generation
- HTML documentation with embedded search functionality
- Datomic schema files (`.edn` format)
- Alzabo schema files (canonical `.edn` format)
- Static file output to configurable paths (typically `resources/public/schema/{{version}}/`)

## Project Structure Notes
- This is a Leiningen project with mixed Clojure/ClojureScript codebase
- Uses Figwheel for ClojureScript hot-reloading during development
- Test resources include sample schemas and configurations
- Main use case is as a library, CLI is somewhat deprecated according to code comments