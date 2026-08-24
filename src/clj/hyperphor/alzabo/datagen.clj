(ns hyperphor.alzabo.datagen
  "Generate example data for Alzabo schemas"
  (:require [hyperphor.alzabo.schema :as schema]
            [hyperphor.multitool.core :as u]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [hyperphor.ellum.core :as llm])
  (:import [java.time LocalDate LocalDateTime]
           [java.time.format DateTimeFormatter]
           [java.util UUID]))

;;; Core data generation framework

;;; TODO I hate this, just use an atom or pass an arg. Also not really used. 
(def ^:dynamic *generation-context*
  "Context for data generation including generated entities and configuration"
  {:entities {}           ; Map of kind -> [generated entities]
   :entity-counts {}      ; Map of kind -> target count
   :relationships {}      ; Map of [from-kind to-kind] -> [(from-id to-id)]
   :llm-enabled? true
   :seed 42})

(defn reset-context!
  "Reset the generation context"
  [config]
  (alter-var-root #'*generation-context*
                  (constantly (merge {:entities {}
                                      :entity-counts {}
                                      :relationships {}
                                      :llm-enabled? true
                                      :seed 42}
                                     config))))

(defn add-entity!
  "Add a generated entity to the context"
  [kind entity]
  (alter-var-root #'*generation-context*
                  update-in [:entities kind] (fnil conj []) entity))

(defn get-entities
  "Get all entities of a given kind"
  [kind]
  (get-in *generation-context* [:entities kind] []))


;;; TODO need a better mechanism and should be in schema
(defn object-label
  [obj]
  (or (:name obj)
      (:title obj)))

(defn context-string
  [object]
  (u/expand-template
   "related to the {{kind}} {{label}}"
   (-> object
       (assoc :label (object-label object)))))

(defn add-kind
  [kind obs]
  (map #(assoc % :kind kind) obs))

;;; Data sometimes is wrapped in extraneous stuff
(defn regularize
  [x]
  (if (and (sequential? x)
           (map? (first x)))
    x
    (when (sequential? x)
      (u/some-thing regularize x))))

;;; TODO fix ::keywords
;;; TODO Need richer context, with attributes and multiple.
;;; Uses JSON format which might be easier than trying to do edn.
(defn generate-entities
  "Ask LLM to generate entities based on schema.
  context is a related object (eg a Band for generating Songs)
  kind-modifier is text to add to kind/description (eg \"fictional\") 
  "
  [kind schema & {:keys [count context kind-modifier] :as params}]
  (let [params (merge  {:count 10 :kind-modifier ""} params)
        sdef (schema/kind-def schema kind)]
    (assert sdef "Kind not found in schema")
    (->> (u/expand-template
                     "Please give me a list of {{count}} {{kind-modifier}} {{id}} {{context-string}} as a list of maps in json format. For each, include the following fields: {{field-list}}. Return a json-formatted list of entities, with no extraneous text. Generate correct json without comments or ellipses."
                     (-> sdef
                         (merge params)
                         (assoc :field-list (str/join ", " (map name (keys (:fields sdef)))))
                         (assoc :context-string (if context (context-string context) ""))
                         ))
        llm/query-json
        ;; TODO clean format (or use structured response), keys, turn "" to nil
        regularize
        (add-kind kind)
        )))
                             
(comment
  (generate-entities :Genre schema))


;;; High-level generation orchestration

(defn kind-dependencies
  "Get the kinds that a given kind depends on (via field references)"
  [schema kind-name]
  (let [kind-def (get-in schema [:kinds kind-name])
        fields (:fields kind-def)
        all-kinds (set (keys (:kinds schema)))
        primitives schema/primitives]
    (->> fields
         vals
         (map :type)
         ;; Handle tuples - extract types from vectors
         (mapcat (fn [t] (if (vector? t) t [t])))
         ;; Filter to only kind references (not primitives, not enums)
         (filter keyword?)
         (filter all-kinds)
         (remove primitives)
         ;; Remove self-references for data generation ordering
         (remove #{kind-name})
         distinct
         vec)))

(defn topological-sort
  "Topological sort of kinds based on dependencies.
   Returns a sequence of kinds in dependency order (dependencies first).
   Handles cycles by breaking them arbitrarily."
  [schema]
  (let [all-kinds (keys (:kinds schema))
        deps-map (into {} (map (fn [k] [k (kind-dependencies schema k)]) all-kinds))]
    (loop [result []
           remaining (set all-kinds)
           deps deps-map]
      (if (empty? remaining)
        result
        (let [;; Find kinds with no remaining dependencies
              no-deps (filter (fn [k]
                               (every? (complement remaining) (get deps k)))
                             remaining)]
          (if (seq no-deps)
            ;; Process kinds with no dependencies
            (recur (into result no-deps)
                   (apply disj remaining no-deps)
                   deps)
            ;; Cycle detected - just pick one arbitrarily
            (let [next-kind (first remaining)]
              (recur (conj result next-kind)
                     (disj remaining next-kind)
                     deps))))))))

(defn determine-generation-order
  "Determine the order to generate entities based on field dependencies.
   Kinds that are referenced by others are generated first."
  [schema]
  (topological-sort schema))

(defn generate-sample-data
  "Generate sample data for an entire schema"
  [schema & {:keys [entity-counts llm-enabled? seed]
             :or {entity-counts {}
                  llm-enabled? true
                  seed 42}}]
  (reset-context! {:llm-enabled? llm-enabled? :seed seed})

  ;; Set default entity counts
  (let [default-counts (into {} (map (fn [kind] [kind 5]) (keys (:kinds schema))))
        final-counts (merge default-counts entity-counts)]

    ;; Generate entities in dependency order
    (doseq [kind (determine-generation-order schema)]
      (let [kind-def (get-in schema [:kinds kind])
            count (get final-counts kind 0)]
        (when (> count 0)
          (println (str "Generating " count " " (name kind) " entities..."))
          (doseq [entity (generate-entities kind kind-def schema count)]
            (add-entity! kind entity)))))

    ;; Return the generated data
    (:entities *generation-context*)))

