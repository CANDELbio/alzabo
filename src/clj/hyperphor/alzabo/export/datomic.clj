(ns hyperphor.alzabo.export.datomic
  (:require [hyperphor.multitool.core :as u]
            [hyperphor.alzabo.schema :as alzs]
            ))

;;; Write out a Datomic schema from Alzabo schema

;;; See https://docs.datomic.com/on-prem/schema.html 
(def primitive-types #{:string :boolean :float :double :long :bigint :bigdec :instant :keyword :uuid})

(defn- az-type->datomic-type
  [az-type]
  (cond (or (map? az-type) (vector? az-type))
        :db.type/tuple
        (az-type primitive-types)
        (keyword "db.type" (name az-type))
        :else
        :db.type/ref))

(defn safe-name
  [v]
  (if (number? (u/ignore-errors (read-string (name v))))
    (str "v-" (name v))
    (name v)))

(defn datomic-schema
  "Generate a Datomic schema transaction from an Alzabo schema"
  [{:keys [kinds enums] :as schema} & [{:keys [enum-doc?] :or {enum-doc? true}}]]
  (alzs/validate-schema schema)
  (u/clean-walk
   (concat
    (mapcat (fn [[class-name class-def]]
              ;; Use all-fields to include inherited fields (flattens inheritance for Datomic)
              (map (fn [[field-name {:keys [cardinality type unique? unique-id index component doc] :as field-def}]]
                     (let [datomic-type (az-type->datomic-type type)]
                       {:db/ident (keyword (name class-name) (name field-name))
                        :db/doc doc
                        :db/cardinality (if (= :many cardinality) :db.cardinality/many :db.cardinality/one)
                        :db/unique (when unique? :db.unique/identity) ;TODO support :db.unique/value see https://docs.datomic.com/schema/identity.html
                        :db/index index
                        :db/valueType datomic-type
                        ;; heterogenous tuples https://docs.datomic.com/on-prem/schema.html
                        :db/isComponent (when (= :db.type/ref datomic-type)
                                          (if component true false))
                        :db/tupleTypes (when (vector? type)
                                         (mapv az-type->datomic-type type))
                        :db/tupleType (when (map? type)
                                        (az-type->datomic-type (get type ':*)))
                        }))
                   (alzs/all-fields schema class-name)))
            kinds)
    ;; Note enum-type is thrown on the floor; no real place to put it
    (mapcat (fn [[enum-type {:keys [values doc]}]]
              (map (fn [[enum doc]]
                     (u/clean-map
                      {:db/ident (keyword (name enum-type) (safe-name enum))
                       :db/doc (and enum-doc? doc)
                       }))
                   values))
            enums))
   nil?
   ))








#_
(defn metamodel 
  "Generate a Pret metamodel from an Alzabo schema"
  [{:keys [kinds enums] :as schema} & [{:keys [enum-doc?] :or {enum-doc? true}}]]
  (let [metamodel-fixed (read-edn "resources/candel/metamodel-fixed.edn")
        entity-metadata
        (for [[kind {:keys [unique-id parent label]}] kinds]
          (u/clean-map
           {:kind/name kind
            :kind/need-uid unique-id     ;TODO Not quite right
            :kind/parent parent
            :kind/context-id label       ;TODO ?
            }))
        reference-meta-attributes
        (filter
         identity
         (mapcat (fn [[kind {:keys [fields]}]]
                   (map (fn [[field {:keys [type]}]]
                         (when (not (get schema/primitives type))
                           {:db/id (keyword (name kind) (name field))
                            :ref/from kind 
                            :ref/to type}
                           ))
                       fields))
                kinds))
        ]
    [metamodel-fixed entity-metadata reference-meta-attributes]))
