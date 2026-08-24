(ns hyperphor.alzabo.export.datomic-test
  (:require [hyperphor.alzabo.export.datomic :refer :all]
            [hyperphor.alzabo.schema :as schema]
            [clojure.test :refer :all]
            ))

;;; Basic smoke test
(deftest test-datomic-gen
  (let [a-schema (schema/read-schema "test/resources/schema/rawsugar.edn")
        d-schema (datomic-schema a-schema)
        project-name (some #(and (= :project/name (:db/ident %)) %)
                           d-schema)
        row-files (some #(and (= :row/files (:db/ident %)) %)
                        d-schema)]
    (is (= 27 (count d-schema)))
    (is (= {:db/ident :project/name
            :db/cardinality :db.cardinality/one
            :db/unique :db.unique/identity
            :db/valueType :db.type/string}
           project-name))
    (testing "isComponent always set"
      (prn :row-files row-files)
      (is (false? (:db/isComponent row-files)))
      )))

(deftest test-tuples
  (let [a-schema (schema/read-schema "test/resources/schema/gxp.edn")
        d-schema (datomic-schema a-schema)]
    (testing "heterogenus tuples"
      (is (some #(= % {:db/ident :experiment/results
                       :db/doc "A heterogenous tuple-valued field"
                       :db/valueType :db.type/tuple
                       :db/cardinality :db.cardinality/many
                       :db/tupleTypes [:db.type/ref :db.type/float]})
                d-schema)))
    (testing "homogenous tuples"
      (is (some #(= % {:db/ident :experiment/uid
                       :db/doc "A homogenous tuple-valued field"
                       :db/valueType :db.type/tuple
                       :db/tupleType :db.type/string
                       :db/cardinality :db.cardinality/one
                       })
                d-schema))
      )))
