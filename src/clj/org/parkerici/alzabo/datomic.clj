(ns org.parkerici.alzabo.datomic
  (:require [org.parkerici.multitool.core :as u]
            [org.parkerici.alzabo.schema :as alzs]
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

(defn datomic-schema
  "Generate a Datomic schema transaction from an Alzabo schema"
  [{:keys [kinds enums] :as schema} & [{:keys [enum-doc?] :or {enum-doc? true}}]]
  (alzs/validate-schema schema)
  (u/clean-walk
   (concat
    (mapcat (fn [[class-name class-def]]
              (map (fn [[field-name {:keys [cardinality type unique unique-id index component doc] :as field-def}]]
                     (let [datomic-type (az-type->datomic-type type)]
                       {:db/ident (keyword (name class-name) (name field-name))
                        :db/doc doc
                        :db/cardinality (if (= :many cardinality) :db.cardinality/many :db.cardinality/one)
                        :db/unique (or (if unique (keyword "db.unique" (name unique)))
                                       (if unique-id :db.unique/identity)) ;this is just for backward compatibility, :unique-id is deprecated
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
                   (:fields class-def)))
            kinds)
    ;; Note enum-type is thrown on the floor; no real place to put it
    (mapcat (fn [[enum-type {:keys [values doc]}]]
              (map (fn [[enum doc]]
                     (u/clean-map
                      {:db/ident enum
                       :db/doc (and enum-doc? doc)
                       }))
                   values))
            enums))
   nil?
   ))








