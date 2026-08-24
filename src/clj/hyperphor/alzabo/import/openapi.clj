(ns hyperphor.alzabo.import.openapi
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as s]
            [hyperphor.multitool.cljcore :as ju]
            ))

;;; Convert openapi.yaml files to Alzabo schemas
;;; This is basically a standalone app, TODO package as such.

;;; TODO could pull some Enums out, not doing that yet
;;; TODO there are no descriptions in the schema section of an OpenAPI file,
;;; but the service definitions have doc strings and some of them could be used via a clever hack.

;;; from Voracious
(defn read-yaml
  [file]
  (let [yaml (slurp file)]
    (yaml/parse-string yaml)))

(defn yaml-types
  [openapi-yaml-file]
  (-> openapi-yaml-file
      read-yaml
      ;; Throwing away a lot here, not sure what to do with the service defs
      (get-in [:components :schemas])))

(defn $ref->kind
  [$ref]
  (keyword (last (s/split $ref #"\/"))))  

(defn alzabo-field
  [{:keys [type additionalProperties items $ref]}]
  (cond $ref {:type ($ref->kind $ref)}
        (get additionalProperties :$ref) 
        {:type ($ref->kind (get additionalProperties :$ref))}
        (or (= type "array")            ;not sure why both of these exist, or if they are different
            (= (get items :type) "array"))
        {:type (or (and (get items :type) ;Note: not exactly right, an array is ordered
                                         (keyword (get items :type)))
                                    (and (get items :$ref)
                                         ($ref->kind (get items :$ref))))
         :cardinality :many}
        :else
        {:type (keyword type)}))

(defn alzabo-schema
  [yaml-types]
  {:version "1.0"
   :kinds
   (into {}
         (map (fn [[kind {:keys [type properties enum required]}]]
                (when-not (= type "object") (prn :non-object kind))
                [kind
                 {:fields (into {}
                                (map (fn [[prop field-def]]
                                       [prop (alzabo-field field-def)])
                                     properties))
                  }])
              yaml-types))})

;;; Convert OpenAPI YAML file to Alzabo schema
(defn convert
  [openapi alzabo]
  (->> openapi
       yaml-types
       alzabo-schema
       (ju/schppit alzabo)))

#_
(convert "resources/petstore.yaml"
         "resources/petstore.edn")
      
