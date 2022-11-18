(ns org.parkerici.alzabo.schema
  (:require [clojure.spec.alpha :as s]))

(def primitives #{:long :float :string :boolean :instant :keyword})

;;; Schema spec

(s/def ::type (s/or :key keyword?       ;TODO check that these are actual types (kinds or primitives)
                    :vec (s/coll-of ::type  :kind vector?) ;for heterogenous tuples
                    :map (s/keys :req-un [*])              ;for homogenous tuples
                    ))

(s/def ::cardinality #{:one :many})
(s/def ::doc string?)

;;; I want to say that ONLY these keys are allowed, which would catch some
;;; errors. But apparently that is unClojurish or something?
(s/def ::field (s/keys :req-un [::type]
                       :opt-un [::cardinality ::doc ::unique ::index ::attribute]))

(s/def ::fields (s/map-of keyword? ::field))

(s/def ::kind (s/keys :req-un [::fields] ;::inherits, but CANDEL not using that.
                      :opt-un [::doc ::reference?]))
                      
(s/def ::kinds (s/map-of keyword? ::kind :conform-keys? true))

;;; Enum :values is a map of keywords (db/ident) to a doc string
(s/def ::values (s/map-of keyword? string?))

(s/def ::enum (s/keys :req-un [::values]
                      :opt-un [::doc]))

(s/def ::enums (s/map-of keyword? ::enum :conform-keys? true))

(s/def ::version string?)

(s/def ::schema (s/keys :req-un [::kinds]
                        :opt-un [::enums ::version ::title]))

(defn validate-schema [schema]
  (if (s/valid? ::schema schema)
    schema
    (throw (ex-info "Schema invalid" {:explanation (s/explain-str ::schema schema)}))))

#?
(:clj
 (defn read-schema
   [source]
   (validate-schema
    (read-string (slurp source)))))
