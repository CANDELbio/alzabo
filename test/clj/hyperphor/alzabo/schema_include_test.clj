(ns hyperphor.alzabo.schema-include-test
  (:require [hyperphor.alzabo.schema :as schema]
            [clojure.test :refer :all]))

(deftest include-test
  (let [merged (schema/read-schema "test/resources/schema/include/okc-radiohead.edn")]
    ;; new kind
    (is (= #{:subject :sample :illness}
           (set (keys (:kinds merged)))))
    ;; add field to existing kind
    (is (= #{:id :age :diagnosis}
           (set (keys (get-in merged [:kinds :subject :fields] )))))
    ;; TODO enums and other subtleties
    ))
           
