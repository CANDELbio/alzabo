(ns hyperphor.alzabo.import.candel
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.shell :as shell]
            [hyperphor.alzabo.config :as config]
            [hyperphor.alzabo.schema :as schema]
            [hyperphor.multitool.core :as u]))

;;; Converts CANDEL [meta]schema into an Alzabo schema

;;; Utilities

(defn read-edn [file]
  (read-string (slurp file)))

(defn ns->key [namespaced]
  (and namespaced
       (keyword (name namespaced))))

(defn ns-ns->key [namespaced]
  (keyword (namespace namespaced)))

(defn dotted
  [thing]
  (str/replace (name thing) #"\-" "."))

;;; Processing 

(defn kind-fields
  [kind basic-atts]
  (filter #(= kind (ns-ns->key (:db/ident %)))
          basic-atts))

(defn kind-refs
  [kind reference-meta]
  (filter #(= kind (:ref/from %)) reference-meta))

(defn field-index
  [basic-atts reference-meta]
  (let [basic-index (zipmap (map :db/ident basic-atts)
                            basic-atts)
        meta-index (zipmap (map :db/id reference-meta)
                           reference-meta)]
    (u/merge-recursive basic-index meta-index)))

;;; Special cases where we can't deduce the type of an enum field.
;;; Ideally the metamodel would have this information.
(def special-case-enums
  {[:clinical-observation :dfi-reason] :clinical-observation.event-reason
   [:clinical-observation :pfs-reason] :clinical-observation.event-reason
   [:clinical-observation :ttf-reason] :clinical-observation.event-reason
   [:clinical-observation :os-reason] :clinical-observation.event-reason
   [:variant :feature-type] :variant.feature})

(defn lookup-enum
  [kind field enums]
  (let [enum-name (keyword (str (name kind) "." (name field)))]
    (cond (get enums enum-name) enum-name
          (get enums field) field
          (get special-case-enums [kind field])
          (get special-case-enums [kind field])
          :else (do (println "No enum found:" {:kind kind :field field})
                   :ref))))

;;; This does most of the work of translating between datomic and alzabo formats
(defn annotated-field
  [kind field field-index enums]
  (let [namespaced (keyword (name kind) (name field))
        info (get field-index namespaced)
        bare-type (or (get info :ref/to)
                      (ns->key (get info :db/valueType)))
        real-type (cond (= :ref bare-type)
                        (lookup-enum kind field enums)
                        (= :tuple bare-type)
                        (cond (get info :db/tupleType) ;homogenous tuple
                              {:* (ns->key (get info :db/tupleType))}
                              (get info :db/tupleTypes) ;heterogenous tuple
                              (mapv ns->key (or (get info :ref/tuple-types) ;metamodel-level types
                                                (get info :db/tupleTypes) ))
                              true
                              (throw (ex-info "Couldn't determine tuple type" {:kind kind :field field})))
                          
                        true
                        bare-type)]
    [field
     {:type real-type
      :cardinality (ns->key (get info :db/cardinality))
      :unique? (ns->key (get info :db/unique))
      :component? (get info :db/isComponent)
      :doc (get info :db/doc)
      :attribute namespaced
      }]))

(defn read-enums
  "Returns [enums version], where enums is a map of enum names (keyword) to list of possible values"
  []
  (let [raw (read-edn (config/realize-path (str (config/config :candel-schema-path) "/enums.edn")))
        version (some #(and (= :candel/schema (:db/ident %))
                            (:candel.schema/version %))
                      raw)]
    [(->> raw
          (map :db/ident)
          (group-by (comp keyword namespace))
          (u/map-values (fn [values] {:values (zipmap values (map name values))})))
     version]))

(defn produce-schema
  []
  {:post [(schema/validate-schema %)]}
  (let [basic-atts (read-edn (config/realize-path (str (config/config :candel-schema-path) "/schema.edn")))
        [_ entity-meta reference-meta] (read-edn (config/realize-path (str (config/config :candel-schema-path) "/metamodel.edn")))
        field-index (field-index basic-atts reference-meta)
        kinds (map :kind/name entity-meta)
        kind-defs (map (fn [em]
                         {:parent (:kind/parent em)
                          ;; TODO I think this will have to change to allow metamodel to be derived...maybe
                          :unique-id (u/de-ns (or (:kind/need-uid em) (:kind/context-id em)))
                          :label (u/de-ns (:kind/context-id em))
                          :category (if (:kind/ref-data em) :reference :experimental) })
                       entity-meta)
        [enums version] (read-enums)
        kinds
        (into {}
              (map (fn [kind kind-def]
                     (let [basic-att-fields (map #(ns->key (:db/ident %))
                                                 (kind-fields kind basic-atts))
                           ref-fields (map (comp ns->key :db/id) (kind-refs kind reference-meta))
                           all-fields (set/union (set basic-att-fields) (set ref-fields))
                           annotated-fields (into {} (map #(annotated-field kind % field-index enums) all-fields))]
                       [kind (assoc kind-def :fields annotated-fields)]))
                   kinds kind-defs))]
    (u/clean-walk
     {:title "CANDEL"
      :version version
      :kinds kinds
      :enums enums
      :categories {:reference {:color "moccasin" :label "Reference"},
                   :experimental {:color "lightsteelblue" :label "Experimental"}}})))


          
