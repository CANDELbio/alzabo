(ns hyperphor.alzabo.json
  (:require [hyperphor.multitool.core :as u]
            [hyperphor.multitool.cljcore :as ju]
            [clojure.data.json :as json]
            [clojure.walk :as w]))

;;; Support for reading json configs (easier to generate from TypeORM and other sources)

(defn read-schema
  [json-file]
  (let [raw (json/read-str (slurp json-file) :key-fn keyword)]
    (update raw :kinds
            (fn [kinds]
              (u/map-values
               (fn [kdef]
                 (update kdef :fields
                         (fn [fields]
                           (u/map-values
                            (fn [fdef]
                              (-> fdef
                                  (update :cardinality keyword)
                                  (update :type (fn [ot]
                                                  ;; TODO use schema/primitives
                                                  (if (contains? #{"String" "Number"} ot)
                                                    (keyword (clojure.string/lower-case ot))
                                                    (keyword ot))))))
                            fields))))
               kinds)))))

;;; Needs work
#_
(defn write-schema
  []
  (->> "typeorm-schema.json"
       config/realize-path
       read-schema
       #_ enhance-schema
       (ju/schppit (config/realize-path "typeorm-schema.edn"))))
