(ns hyperphor.alzabo.import.graphql
  (:require
   #_ [com.walmartlabs.lacinia.parser.schema :as ls]
   [hyperphor.multitool.core :as u]
   [clojure.string :as str]
   [hyperphor.alzabo.schema :as schema]
   )
  )

;;; Convert GraphQL Schema (SDL) into an Alzabo schema
;;; TODO not wired into CLI yet

;;; Not quite a full mapping but close
(defn type->alz
  [t]
  (cond (keyword? t) {:type t}
        (symbol? t) {:type (keyword (str/lower-case (name t)))}
        (and (list? t) (= 'non-null (first t)))
        (assoc (type->alz (second t)) :required? true)
        (and (list? t) (= 'list (first t)))
        (assoc (type->alz (second t)) :cardinality :many)))
        
(defn field->alz
  [fd]
  (assoc (type->alz (:type fd))
         :doc (:description fd)))

(defn schema->alz
  [schema]
  (u/clean-maps
   {:kinds
    (u/map-values
     (fn [obdef]
       {:doc (:description obdef)
        :fields (u/map-values field->alz (:fields obdef))})
     (:objects schema))
    :enums
    (u/map-values
     (fn [{:keys [description values]}]
       {:doc description        ;TODO is this actually used
        :values (zipmap (map :enum-value values) (map (comp schema/humanize :enum-value) values))
        }
       )
     (:enums schema))
    }))



(comment
  (def ls (ls/parse-schema (slurp "/opt/mt/repos/exobrain/resources/pharmakb-schema2.graphql")))
  (spit "/opt/mt/repos/candelbio/alzabo/resources/pharmakb-schema.edn" (schema->alz ls)))

