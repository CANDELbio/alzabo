(ns hyperphor.alzabo.roundtrip
  (:require [clojure.data :as data]
            [clojure.pprint :refer [pprint]]
            [hyperphor.alzabo.output :as output]
            [hyperphor.alzabo.export.datomic :as datomic]
            [hyperphor.alzabo.import.candel :as candel]
            [hyperphor.alzabo.config :as config]
            [hyperphor.multitool.core :as u]))

;;; TODO finish?
;;; Experiments in roundtripping. 

;;; for now, use branch cdel-441-diff-merge, corresponds to what george sent me 7/8/2020

;;; Note: add to metamodel.edn the line
;;;  {:kind/name        :import}
;;; for better results (ask Cognitect to do this)


(defn roundtrip-test []
  (let [candel-orig (concat
                     (candel/read-edn (str (config/config :pret-path) "/resources/schema/schema.edn"))
                     (candel/read-edn (str (config/config :pret-path) "/resources/schema/enums.edn")))
        
        alz (candel/produce-schema nil)    ;use current checked out version
        _ (output/write-schema alz "roundtrip-alz.edn")
        recreated (datomic/datomic-schema alz {:enum-doc? false})
        printer (fn [name contents]
                  (prn name (count contents))
                  (pprint contents))]
    (printer :orig-recreated (u/ldifference candel-orig recreated))
    (printer :recreated-orig (u/ldifference recreated candel-orig))
    (output/write-schema recreated "roundtrip-datomic.edn")))

;;; TODO test that roundtrip-datomic.edn is equivalent to "/resources/schema/schema.edn"
(defn compare-schemas [s1 s2]
  (let [s1i (u/index-by :db/ident s1)
        s2i (u/index-by :db/ident s2)]
    (data/diff s1i s2i)))

#_
(compare-schemas
 (candel/read-edn (str candel/pret-path "/resources/schema/schema.edn"))
 (candel/read-edn "roundtrip-datomic.edn"))


;;; TODO recreate and eval metamodel.edn
