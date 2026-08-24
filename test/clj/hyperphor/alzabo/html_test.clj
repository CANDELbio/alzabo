(ns hyperphor.alzabo.html-test
  (:require [clojure.test :refer :all]
            [hyperphor.alzabo.schema :as schema]
            [me.raynes.fs :as fs]
            [hyperphor.alzabo.html :refer :all]))

;;; Basic smoke test
(deftest test-html-gen
  (let [schema (schema/read-schema "test/resources/schema/rawsugar.edn")
        output-path (str (fs/temp-dir "alzabo") "/")]
    (schema->html schema output-path)
    (let [files (map fs/split-ext (fs/list-dir output-path))]
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
               ["client" ".js"]
               }
             (set files)))
      ;; TODO file contents
      )))

(deftest test-tuples
  (let [schema (schema/read-schema "test/resources/schema/gxp.edn")
        output-path (str (fs/temp-dir "alzabo") "/")]
    (schema->html schema output-path)
    (let [experiment (slurp (str output-path "experiment.html"))]
      ;; Test that type link rendered properly
      (is (re-find (re-pattern "[<a href=\"gene.html\"> float]") experiment)))))

