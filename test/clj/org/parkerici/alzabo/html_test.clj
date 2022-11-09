(ns org.parkerici.alzabo.html-test
  (:require [clojure.test :refer :all]
            [org.parkerici.alzabo.schema :as schema]
            [org.parkerici.alzabo.config :as config]
            [me.raynes.fs :as fs]
            [org.parkerici.alzabo.html :refer :all]))

;;; TODO this machinery should be for all tests
(def test-config "test/resources/test-config.edn")

(defn with-test-config [f]
  ;; Test with authentication off
  (config/set-config! test-config)
  (f))

(use-fixtures :each with-test-config)

;;; Basic smoke test
(deftest test-html-gen
  (let [schema (schema/read-schema "test/resources/schema/rawsugar.edn")
        test-dir (str (fs/temp-dir "alzabo") "/")]
    (schema->html schema test-dir {:edge-labels? true})
    (let [files (map fs/split-ext (fs/list-dir (str test-dir "/1.0")))]
      (is (= #{["index" ".html"]
               ["operation" ".html"]
               ["row" ".html"]
               ["column" ".html"]
               ["file" ".html"]
               ["project" ".html"]
               ["sheet" ".html"]
               ["cell" ".html"]
               ["schema" ".edn"]
               ["schema" ".dot"]
               ["schema.dot" ".svg"]
               ["schema.dot" ".cmapx"]
               ["alzabo" ".css"]
               ["js" nil]
               }
             (set files)))
      ;; TODO file contents
      )))

(deftest test-tuples
  (let [schema (schema/read-schema "test/resources/schema/gxp.edn")
        test-dir (str (fs/temp-dir "tuples") "/")]
    (schema->html schema test-dir {})
    (let [experiment (slurp (str test-dir "1.0/experiment.html"))]
      ;; Test that type link rendered properly
      (is (re-find (re-pattern "[<a href=\"gene.html\"> float]") experiment)))))

