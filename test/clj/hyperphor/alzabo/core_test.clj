(ns hyperphor.alzabo.core-test
  (:require [hyperphor.alzabo.core :as sut]
            [me.raynes.fs :as fs]
            [clojure.test :refer :all]))

(defn clean []
  (fs/delete-dir "target"))

;;; Cleanup before each test and after all tests
(use-fixtures :each (fn [f] (clean) (f)))
(use-fixtures :once (fn [f] (f) (clean)))

(def rawsugar-config "test/resources/rawsugar-config.edn")
(def candel-config "test/resources/candel-config.edn")

(deftest test-datomic-gen
  (testing "from file"
    (sut/main-guts rawsugar-config "datomic")
    (is (fs/exists? "target/rawsugar/datomic-schema.edn")))
  #_
  (testing "from CANDEL"
    (sut/main-guts candel-config "datomic")
    (is (fs/exists? "target/candel/1.3.1/alzabo-schema.edn"))
    (is (fs/exists? "target/candel/1.3.1/datomic-schema.edn"))))

(deftest test-html-gen
  (testing "from file"
    (sut/main-guts rawsugar-config "documentation")
    (is (fs/exists? "target/rawsugar/schema.edn"))
    (is (fs/exists? "target/rawsugar/schema.dot.svg"))
    (is (fs/exists? "target/rawsugar/sheet.html")))
  #_
  (testing "from CANDEL"
    (sut/main-guts candel-config "documentation" )
    (is (fs/exists? "target/candel/1.3.1/schema.edn"))
    (is (fs/exists? "target/candel/1.3.1/schema.dot.svg"))
    (is (fs/exists? "target/candel/1.3.1/dataset.html"))))

