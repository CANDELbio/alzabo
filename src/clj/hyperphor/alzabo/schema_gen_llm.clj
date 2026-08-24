(ns hyperphor.alzabo.schema-gen-llm
  (:require [hyperphor.multitool.core :as u]
            [clojure.string :as str]
            [hyperphor.ellum.core :as llm]
            [hyperphor.ellum.extract :as llme]
            [hyperphor.alzabo.schema :as schema]))


(def system-prompt
  "You are a knowledge representation expert who knows how to create clean and elegant ontologies and schemas for various domains")

(def sample-schema "resources/jazz-schema.edn")
(def sample-schema "test/resources/schema/jazz.edn")

;;; Phase 1: enumerate the kinds (entity types) for the domain before writing any fields.
;;; This forces the model to think about the full entity model first, so phase 2 can
;;; use reference types instead of lazily falling back to :string.
(defn- generate-kinds
  [domain extra]
  (let [query (u/tx "List all significant entity types (kinds) needed for a {{domain}} domain schema. {{extra}}
Include not just the main entities but also supporting types that are often lazily represented as strings — things like anatomical parts, material types, classifications, controlled vocabularies, etc. that benefit from being first-class entities with their own attributes.
Return ONLY a Clojure map (no prose) of keyword kind-names to brief description strings.
Example: {:Fossil \"A preserved specimen\" :AnatomicalPart \"A body part or skeletal element\" :Taxon \"A taxonomic unit\"}")]
    (-> {:model "gpt-4.1"
         :messages [{:role "system" :content system-prompt}
                    {:role "user" :content query}]}
        llm/complete
        (get-in [:choices 0 :message :content])
        llme/extract-edn
        )))

;;; Phase 2: generate full field definitions, with the kinds list in context so the model
;;; knows what reference types are available and uses them instead of :string.
(defn- generate-schema-from-kinds
  [domain kinds-map extra]
  (let [kinds-list (str/join ", " (map name (keys kinds-map)))
        query (u/tx "Create a complete Alzabo schema for the {{domain}} domain using exactly these kinds: {{kinds-list}}.
For each kind, define its fields with :type, :cardinality (when :many), :doc, and for string fields :examples with 2-3 representative values.
IMPORTANT: whenever a field represents a concept that exists as a kind in the list above, use a reference type (the kind keyword) rather than :string.
{{extra}}")]
    (-> {:model "gpt-4.1"
         :messages [{:role "system" :content system-prompt}
                    {:role "user" :content query}
                    {:role "user" :content (str "kinds with descriptions: " (pr-str kinds-map))}
                    {:role "user" :content (str "example schema format: " (slurp sample-schema))}]}
        llm/complete
        (get-in [:choices 0 :message :content])
        llme/extract-clojure
        first)))


;;; The new model doesn't do subtypes. Should have a way to try this one. 
(comment
  ;; old sgen-prompt
  "Create an Alzabo schema for the %s domain, using the example as a guide. Include classes, attributes, and relations. For each attribuate and relation, include a type and a documentation string. Try to include some subtype (extends) relations"
    )


(defn sgen
  [domain & [extra]]
  (let [extra (or extra "")
        kinds-map (generate-kinds domain extra)]
    (prn :kinds kinds-map)
    (generate-schema-from-kinds domain kinds-map extra)))

;;; Adds documentation strings to CANDEL, which is a bit lacking in that regard
(defn add-doc
  [domain schema]
  (let [query (format "Given this Alzabo schema for the %s domain, add documentation to each kind, attribute, and enum value if id doesn't already exist" domain)]
    (-> {:model "gpt-4.1"
         :messages [{:role "system" :content system-prompt}
                    {:role "user" :content query}
                    {:role "user" :content (str "schema: " (print-str schema))}
                    ]}
        llm/complete
        (get-in [:choices 0 :message :content])
        ;; Produces incorrect edn with ellipses, so extracted and edited by hand
        #_ llm/extract-clojure
        #_ first
        )))

(defn improve-doc
  [domain schema]
  (let [query (format "Given this Alzabo schema for the %s domain, improve the documentation string for each kind, attribute, and enum value, make it more human readable. Return a new improved schema in the same format" domain)]
    (-> {:model "gpt-4.1"
         :messages [{:role "system" :content system-prompt}
                    {:role "user" :content query}
                    {:role "user" :content (str "schema: " (print-str schema))}
                    ]}
        llm/complete
        (get-in [:choices 0 :message :content])
        ;; Produces incorrect edn with ellipses, so extracted and edited by hand
        #_ llm/extract-clojure
        #_ first
        )))


(comment
  (def schema (hyperphor.alzabo.import.candel/produce-schema))
  (def doc (add-doc "cancer immunotherapy research" schema))
  ;; Some hand tweaking
  (def doc (read-string (slurp "resources/candel/llm-doc.edn")))

  (hyperphor.alzabo.output/write-schema
   (u/merge-recursive schema doc)
   "resources/public/schema/candel/schemax.edn")

  (hyperphor.alzabo.core/demo  "resources/public/schema/candel/schemax.edn" "candelx")
  )


(comment
  (def schema (schema/read-schema "/opt/mt/repos/pici/okc/resources/schema.alz.edn"))
  )
