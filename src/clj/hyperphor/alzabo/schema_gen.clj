(ns hyperphor.alzabo.schema-gen
  (:require [hyperphor.multitool.core :as u]
            [clojure.string :as str]
            [camel-snake-kebab.core :as csk]))

;;; Tools for generating schemas

;;; Generate schema from .csv data or other mapseq

;;; Assumes all items have same fields, no nulls, which might not always be the case
(defn- infer-field-def
  [vals]
  (let [dvals (distinct vals)]
    (cond
      (and (<= (count dvals) 2)
           (every? #{0 1 true false "TRUE" "FALSE" "true" "false"} dvals))
      {:type :boolean}
      (every? int? dvals) {:type :long :min (u/min* dvals) :max (u/max* dvals) }
      (every? number? dvals) {:type :float :min (u/min* dvals) :max (u/max* dvals) }
      (> (count dvals) 40) {:type :string}
      :else
      {:type :enumerated :values (sort (map str dvals))});the str is because sometimes ints creep in and it breaks sort
    )) 

(defn field-name
  [col kind]
  (-> col
      name
      (str/replace "/" "-")
      (str/replace "," "-")      
      csk/->kebab-case                  ;TODO this is kind of crappy because it breaks irAE up
      (str/replace #"ir\-ae" "irAE")    ;TEMP so special casing
      (str/replace (re-pattern (str (name kind) ".")) "")
      keyword))

(defn mapseq->schema-fields
  [mapseq kind]
  (let [keys (keys (first mapseq))]
    (into
     {}
     (for [k keys]
       (let [field-name (field-name k kind)]
         [field-name (assoc (infer-field-def (u/mapf k mapseq))
                            :doc (name k))          ;record this for posterity (TODO maybe on :dbcol)
          ]
         )))))



