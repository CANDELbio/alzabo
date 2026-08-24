## Alzabo schema format

Schemas are represented as EDN maps. See [an example](test/resources/schema/rawsugar.edn) or the [schema spec](src/cljc/hyperphor/alzabo/schema.cljc).

- `:title` a string
- `:version` a string
- `:include` an array of pathnames to schemas to merge in
- `:kinds` a map of kind names (keywords) to kind definitions (see below)
- `:enums` A map of enum names (keywords) to sequence of enum values (also keywords, generally namespaced)

A kind definition is a map with attributes:
- `:fields`: a map of field names (keywords) to field definitions
- `:description` a string

A field definition is a map with attributes:
- `:type` can be:
 - a keyword, either a kind name, a primitive
 - a vector of types (defines a Datomic heterogenous tuple)
 - a map of the form `{:* <type>}` (defines a Datomic homogenous tuple)
   Default is `:string`
-`:doc` a string
-`:cardinality` Either `:one` (default) or `:many`
- `:unique?` Either `:identity` or `:value`, see [Datomic doc](https://docs.datomic.com/on-prem/schema.html#operational-schema-attributes) for details.
-`:unique-id` (deprecated) `true` means the same as `:unique :identity`
- `:attribute` the datomic or sparql attribute corresponding to the field 

The defined primitives are `#{:string :boolean :float :double :long :bigint :bigdec :instant :keyword :uuid}`. 
