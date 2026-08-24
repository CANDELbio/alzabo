(ns hyperphor.alzabo.schema-inheritance-test
  (:require [hyperphor.alzabo.schema :as schema]
            [hyperphor.alzabo.export.datomic :as datomic]
            [clojure.test :refer :all]))

(def test-schema-basic
  "Schema with single inheritance"
  {:kinds
   {:animal
    {:fields
     {:name {:type :string :doc "Animal name"}
      :age {:type :long :doc "Age in years"}}}

    :mammal
    {:extends :animal
     :fields
     {:fur-color {:type :string :doc "Color of fur"}}}

    :dog
    {:extends :mammal
     :fields
     {:breed {:type :string :doc "Dog breed"}}}}})

(def test-schema-multiple
  "Schema with multiple inheritance"
  {:kinds
   {:named
    {:fields
     {:name {:type :string :doc "Entity name"}}}

    :timestamped
    {:fields
     {:created-at {:type :instant :doc "Creation timestamp"}
      :updated-at {:type :instant :doc "Update timestamp"}}}

    :person
    {:extends [:named :timestamped]
     :fields
     {:email {:type :string :doc "Email address"}}}}})

(def test-schema-deep-merge
  "Schema testing field deep merge"
  {:kinds
   {:base
    {:fields
     {:shared-field {:type :string :doc "Base documentation"}}}

    :derived
    {:extends :base
     :fields
     {:shared-field {:type :string :doc "Enhanced documentation" :required? true}}}}})

(def test-schema-circular
  "Schema with circular inheritance - should fail validation"
  {:kinds
   {:a {:extends :b :fields {}}
    :b {:extends :c :fields {}}
    :c {:extends :a :fields {}}}})

(def test-schema-missing-parent
  "Schema with missing parent - should fail validation"
  {:kinds
   {:child
    {:extends :nonexistent
     :fields {:foo {:type :string}}}}})

;; Tests

(deftest test-normalize-extends
  (testing "normalize single keyword"
    (is (= [:parent] (schema/normalize-extends :parent))))
  (testing "normalize vector"
    (is (= [:p1 :p2] (schema/normalize-extends [:p1 :p2]))))
  (testing "normalize nil"
    (is (= [] (schema/normalize-extends nil)))))

(deftest test-get-parents
  (testing "single parent"
    (is (= [:animal] (schema/get-parents test-schema-basic :mammal))))
  (testing "multiple parents"
    (is (= [:named :timestamped] (schema/get-parents test-schema-multiple :person))))
  (testing "no parents"
    (is (= [] (schema/get-parents test-schema-basic :animal)))))

(deftest test-get-ancestors
  (testing "single level inheritance"
    (is (= [:animal] (schema/get-ancestors test-schema-basic :mammal))))
  (testing "multi-level inheritance"
    (is (= [:mammal :animal] (schema/get-ancestors test-schema-basic :dog))))
  (testing "multiple inheritance ancestors"
    (let [ancestors (schema/get-ancestors test-schema-multiple :person)]
      (is (= 2 (count ancestors)))
      (is (some #{:named} ancestors))
      (is (some #{:timestamped} ancestors))))
  (testing "no ancestors"
    (is (= [] (schema/get-ancestors test-schema-basic :animal)))))

(deftest test-inherited-fields
  (testing "inherit from single parent"
    (let [fields (schema/inherited-fields test-schema-basic :mammal)]
      (is (= 2 (count fields)))
      (is (contains? fields :name))
      (is (contains? fields :age))))
  (testing "inherit through multiple levels"
    (let [fields (schema/inherited-fields test-schema-basic :dog)]
      (is (= 3 (count fields)))
      (is (contains? fields :name))
      (is (contains? fields :age))
      (is (contains? fields :fur-color))))
  (testing "multiple inheritance"
    (let [fields (schema/inherited-fields test-schema-multiple :person)]
      (is (= 3 (count fields)))
      (is (contains? fields :name))
      (is (contains? fields :created-at))
      (is (contains? fields :updated-at)))))

(deftest test-deep-merge-field
  (testing "doc strings are concatenated"
    (let [parent {:type :string :doc "Parent doc"}
          child {:type :string :doc "Child doc"}
          merged (schema/deep-merge-field parent child)]
      (is (= "Child doc (extends: Parent doc)" (:doc merged)))))
  (testing "child properties override parent"
    (let [parent {:type :string :required? false}
          child {:type :string :required? true}
          merged (schema/deep-merge-field parent child)]
      (is (= true (:required? merged)))))
  (testing "child inherits missing properties"
    (let [parent {:type :string :index true}
          child {:doc "Child doc"}
          merged (schema/deep-merge-field parent child)]
      (is (= true (:index merged)))
      (is (= "Child doc" (:doc merged))))))

(deftest test-all-fields
  (testing "combines local and inherited fields"
    (let [fields (schema/all-fields test-schema-basic :dog)]
      (is (= 4 (count fields)))
      (is (contains? fields :breed))  ; local
      (is (contains? fields :fur-color))  ; from mammal
      (is (contains? fields :name))  ; from animal
      (is (contains? fields :age))))  ; from animal
  (testing "deep merge overrides"
    (let [fields (schema/all-fields test-schema-deep-merge :derived)
          shared (:shared-field fields)]
      (is (= true (:required? shared)))
      (is (clojure.string/includes? (:doc shared) "Enhanced"))
      (is (clojure.string/includes? (:doc shared) "extends:")))))

(deftest test-validation-circular
  (testing "circular inheritance throws error"
    (is (thrown? Exception
                 (schema/validate-schema test-schema-circular)))))

(deftest test-validation-missing-parent
  (testing "missing parent throws error"
    (is (thrown? Exception
                 (schema/validate-schema test-schema-missing-parent)))))

(deftest test-validation-success
  (testing "valid schema with inheritance passes"
    (is (some? (schema/validate-schema test-schema-basic)))
    (is (some? (schema/validate-schema test-schema-multiple)))))

(deftest test-datomic-export-with-inheritance
  (testing "datomic export includes inherited fields"
    (let [d-schema (datomic/datomic-schema test-schema-basic)
          dog-fields (filter #(= "dog" (namespace (:db/ident %))) d-schema)
          dog-field-names (set (map #(name (:db/ident %)) dog-fields))]
      ;; Dog should have all fields: breed (local) + fur-color (mammal) + name, age (animal)
      (is (>= (count dog-fields) 4))
      (is (contains? dog-field-names "breed"))
      (is (contains? dog-field-names "fur-color"))
      (is (contains? dog-field-names "name"))
      (is (contains? dog-field-names "age")))))

