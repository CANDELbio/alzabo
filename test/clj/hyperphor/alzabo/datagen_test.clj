(ns hyperphor.alzabo.datagen-test
  (:require [hyperphor.alzabo.datagen :refer :all]
            [hyperphor.alzabo.schema :as schema]
            [clojure.test :refer :all]))

(def simple-schema
  "Schema with clear dependency chain: A -> B -> C"
  {:kinds
   {:c
    {:fields
     {:name {:type :string}}}

    :b
    {:fields
     {:name {:type :string}
      :c-ref {:type :c}}}

    :a
    {:fields
     {:name {:type :string}
      :b-ref {:type :b}}}}})

(def multi-dependency-schema
  "Schema with multiple dependencies"
  {:kinds
   {:base1
    {:fields
     {:name {:type :string}}}

    :base2
    {:fields
     {:value {:type :long}}}

    :derived
    {:fields
     {:ref1 {:type :base1}
      :ref2 {:type :base2}}}}})

(def self-reference-schema
  "Schema with self-referential kind"
  {:kinds
   {:node
    {:fields
     {:name {:type :string}
      :parent {:type :node}
      :children {:type :node :cardinality :many}}}}})

(def cycle-schema
  "Schema with circular dependencies"
  {:kinds
   {:a
    {:fields
     {:b-ref {:type :b}}}

    :b
    {:fields
     {:c-ref {:type :c}}}

    :c
    {:fields
     {:a-ref {:type :a}}}}})

(def inheritance-schema
  "Schema with inheritance - child depends on kinds parent references"
  {:kinds
   {:address
    {:fields
     {:street {:type :string}}}

    :person
    {:fields
     {:name {:type :string}
      :address {:type :address}}}

    :employee
    {:extends :person
     :fields
     {:employee-id {:type :long}}}}})

(def jazz-subset-schema
  "Subset of jazz schema for testing"
  {:kinds
   {:venue
    {:fields
     {:name {:type :string}}}

    :composition
    {:fields
     {:title {:type :string}}}

    :person
    {:fields
     {:name {:type :string}}}

    :musician
    {:extends :person
     :fields
     {:instrument {:type :string}}}

    :performance
    {:fields
     {:composition {:type :composition}
      :performers {:type :musician :cardinality :many}}}

    :live-performance
    {:extends :performance
     :fields
     {:venue {:type :venue}}}}})

;; Tests

(deftest test-kind-dependencies-simple
  (testing "kind-dependencies extracts field references"
    (is (= [:c] (kind-dependencies simple-schema :b)))
    (is (= [:b] (kind-dependencies simple-schema :a)))
    (is (= [] (kind-dependencies simple-schema :c)))))

(deftest test-kind-dependencies-multi
  (testing "multiple dependencies"
    (let [deps (set (kind-dependencies multi-dependency-schema :derived))]
      (is (= #{:base1 :base2} deps)))))

(deftest test-kind-dependencies-self-reference
  (testing "self-references are removed"
    (is (= [] (kind-dependencies self-reference-schema :node)))))

(deftest test-kind-dependencies-with-inheritance
  (testing "kinds with inheritance only show direct field dependencies"
    (let [deps (set (kind-dependencies inheritance-schema :employee))]
      ;; employee only has direct fields, no inherited fields in dependencies
      ;; (inheritance is a schema concern, not a data generation concern)
      (is (empty? deps)))))

(deftest test-topological-sort-simple
  (testing "simple dependency chain"
    (let [order (topological-sort simple-schema)
          order-map (into {} (map-indexed (fn [i k] [k i]) order))]
      ;; C should come before B, B should come before A
      (is (< (order-map :c) (order-map :b)))
      (is (< (order-map :b) (order-map :a))))))

(deftest test-topological-sort-multi
  (testing "multiple independent dependencies"
    (let [order (topological-sort multi-dependency-schema)
          order-map (into {} (map-indexed (fn [i k] [k i]) order))]
      ;; Both base1 and base2 should come before derived
      (is (< (order-map :base1) (order-map :derived)))
      (is (< (order-map :base2) (order-map :derived))))))

(deftest test-topological-sort-self-reference
  (testing "self-referential kinds can be generated"
    (let [order (topological-sort self-reference-schema)]
      ;; Should successfully generate order without infinite loop
      (is (= [:node] order)))))

(deftest test-topological-sort-cycle
  (testing "cycles are handled"
    (let [order (topological-sort cycle-schema)]
      ;; Should complete without infinite loop
      (is (= 3 (count order)))
      (is (= #{:a :b :c} (set order))))))

(deftest test-topological-sort-jazz
  (testing "jazz schema ordering based on field dependencies"
    (let [order (topological-sort jazz-subset-schema)
          order-map (into {} (map-indexed (fn [i k] [k i]) order))]
      ;; venue should come before live-performance (live-performance has venue field)
      (is (< (order-map :venue) (order-map :live-performance)))
      ;; composition should come before performance (performance has composition field)
      (is (< (order-map :composition) (order-map :performance)))
      ;; musician should come before performance (performance has performers field)
      (is (< (order-map :musician) (order-map :performance))))))

(deftest test-determine-generation-order
  (testing "determine-generation-order uses topological sort"
    (let [order (determine-generation-order simple-schema)]
      (is (sequential? order))
      (is (= 3 (count order)))
      ;; Should be in dependency order
      (let [order-map (into {} (map-indexed (fn [i k] [k i]) order))]
        (is (< (order-map :c) (order-map :b)))
        (is (< (order-map :b) (order-map :a)))))))

(deftest test-no-primitives-in-dependencies
  (testing "primitive types are not treated as dependencies"
    (is (= [] (kind-dependencies simple-schema :c)))
    (is (not-any? #{:string :long :instant}
                  (kind-dependencies inheritance-schema :person)))))
