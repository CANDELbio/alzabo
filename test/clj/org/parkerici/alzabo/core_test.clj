(ns org.parkerici.alzabo.core-test
  (:require [org.parkerici.alzabo.core :as sut]
            [me.raynes.fs :as fs]
            [clojure.test :refer :all]))

(defn clean []
  (fs/delete-dir "resources/schema")
  (fs/delete-dir "resources/public/schema"))

;;; Cleanup before each test and after all tests
(use-fixtures :each (fn [f] (clean) (f)))
(use-fixtures :once (fn [f] (f) (clean)))

(def config "test/resources/test-config.edn")

(deftest test-datomic-gen
  (testing "from file"
    (sut/-main-guts config "datomic" {:source "test/resources/schema/rawsugar.edn"})
    (is (fs/exists? "resources/schema/1.0/datomic-schema.edn")))
  (testing "from CANDEL"
    (sut/-main-guts config "datomic" {:source "candel"})
    (is (fs/exists? "resources/schema/1.3.1/alzabo-schema.edn"))
    (is (fs/exists? "resources/schema/1.3.1/datomic-schema.edn"))))

(deftest test-html-gen
  (testing "from file"
    (sut/-main-guts config "documentation" {:source "test/resources/schema/rawsugar.edn"})
    (is (fs/exists? "resources/public/schema/1.0/schema.edn"))
    (is (fs/exists? "resources/public/schema/1.0/schema.dot.svg"))
    (is (fs/exists? "resources/public/schema/1.0/sheet.html")))
  (testing "from CANDEL"
    (sut/-main-guts config "documentation" {:source "candel"})
    (is (fs/exists? "resources/public/schema/1.3.1/schema.edn"))
    (is (fs/exists? "resources/public/schema/1.3.1/schema.dot.svg"))
    (is (fs/exists? "resources/public/schema/1.3.1/dataset.html"))))

