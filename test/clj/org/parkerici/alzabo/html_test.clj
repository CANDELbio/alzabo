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
  (config/set! :output-path (str (fs/temp-dir "alzabo") "/"))
  (f))

(use-fixtures :each with-test-config)

;;; Basic smoke test
(deftest test-html-gen
  (let [schema (schema/read-schema "test/resources/schema/rawsugar.edn")]
    (schema->html schema)
    (let [files (map fs/split-ext (fs/list-dir (config/config :output-path)))]
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
  (let [schema (schema/read-schema "test/resources/schema/gxp.edn")]
    (schema->html schema)
    (let [experiment (slurp (str (config/config :output-path) "experiment.html"))]
      ;; Test that type link rendered properly
      (is (re-find (re-pattern "[<a href=\"gene.html\"> float]") experiment)))))

