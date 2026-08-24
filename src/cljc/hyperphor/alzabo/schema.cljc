(ns hyperphor.alzabo.schema
  (:require [clojure.spec.alpha :as s]
            [hyperphor.multitool.core :as u]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [clojure.edn :as edn]
            ))

;;; Note: :include keyword is processed out during read so not validated

(def numeric-primitives #{:long :float :number :bigint})
(def primitives (set/union
                 numeric-primitives
                 #{:string :boolean :instant :keyword}))

;;; Schema spec

(s/def ::type (s/or :key keyword?       ;TODO check that these are actual types (kinds or primitives)
                    :vec (s/coll-of ::type  :kind vector?) ;for heterogenous tuples
                    :map (s/keys :req-un [*])              ;for homogenous tuples
                    ))

(s/def ::cardinality #{:one :many})
(s/def ::doc string?)
(s/def ::examples (s/coll-of string?))

;;; Note: this insane rigamarole is so schema can actually detect undefined keys in a map. Should use it eslewhere.
(defmacro strict-keys [& {:keys [req req-un opt opt-un]}]
  (let [;; Extract the actual keyword names for validation
        req-key-names (when req (set req))
        opt-key-names (when opt (set opt))
        req-un-key-names (when req-un (set (map #(keyword (name %)) req-un)))
        opt-un-key-names (when opt-un (set (map #(keyword (name %)) opt-un)))
        
        ;; Combine all allowed keys
        all-keys (set/union (or req-key-names #{})
                           (or opt-key-names #{})
                           (or req-un-key-names #{})
                           (or opt-un-key-names #{}))
        
        ;; Required keys for validation
        required-keys (set/union (or req-key-names #{})
                                (or req-un-key-names #{}))]
    
    `(s/and
       (s/keys ~@(when req [:req req])
               ~@(when req-un [:req-un req-un])
               ~@(when opt [:opt opt])
               ~@(when opt-un [:opt-un opt-un]))
       ;; Only allow specified keys
       #(set/subset? (set (keys %)) ~all-keys)
       ;; Ensure all required keys are present
       ~@(when (seq required-keys)
           [`#(set/subset? ~required-keys (set (keys %)))]))))

(s/def ::field (strict-keys :req-un []
                            :opt-un [::type ::cardinality ::required? ::unique? ::component?
                                     ::doc ::examples
                                     ::index ::attribute
                                     ::min ::max]))


(s/def ::fields (s/map-of keyword? ::field))

(s/def ::extends (s/or :single keyword?
                       :multiple (s/coll-of keyword? :kind vector?)))

(s/def ::kind (s/keys :req-un [::fields]
                      :opt-un [::doc
                               ::uri
                               ::reference?  ;TODO reference is too CANDEL-specific, replace with a kind-labels or tags or something
                               ::extends]))
                      
(s/def ::kinds (s/map-of keyword? ::kind :conform-keys? true))

;;; Enum :values is a map of keywords (db/ident) to a doc string
(s/def ::values (s/map-of keyword? string?))

(s/def ::enum (s/keys :req-un [::values]
                      :opt-un [::doc]))

(s/def ::enums (s/map-of keyword? ::enum :conform-keys? true))

(s/def ::version string?)

(s/def ::uri (s/or :string string? :key keyword?))

;;; HTML/graph rendering options; presentation details of a schema rather
;;; than deployment config, so they live on the schema itself.
(s/def ::categories (s/map-of keyword? map?))
(s/def ::explanation coll?)
(s/def ::orientation #{:horizontal :vertical})
(s/def ::width number?)
(s/def ::height number?)
(s/def ::edge-labels? boolean?)

(s/def ::schema (s/keys :req-un [::kinds]
                        :opt-un [::enums ::version ::title
                                 ::categories ::explanation ::orientation
                                 ::width ::height ::edge-labels?]))

;; Forward declaration for inheritance validation (defined later in file)
(declare validate-inheritance)

(defn validate-schema [schema]
  (if (s/valid? ::schema schema)
    (validate-inheritance schema)
    (if (nil? (s/explain-data ::schema schema)) ;TODO for some reason even when schema valid, s/valid? fails, but this woorks
      (validate-inheritance schema)
      (throw (ex-info "Schema invalid" {:explanation (s/explain-str ::schema schema)})))))

(defn clean-string
  [s]
  (u/strip-chars "()," s))

;;; Kebab, but handle some strings separately
(defn safe-kebab-case
  [s]
  (if (= \- (last s))
    s
    (csk/->kebab-case s)))

(defn reduce-old-candel-shit
  [s]
  (if-let [m (re-matches #"\:.*/(.*)" s)]
    (second m)
    s))

(defn clean-enum-value
  [v]
  (-> v
      name
      clean-string
      reduce-old-candel-shit
      safe-kebab-case
      #_ keyword))

(defn humanize
  [term]
  (when term
    (-> term
        name
        (str/replace "_" " "))))

;;;  this is the cheap-ass way to do BK's 2-way structs. Not efficient of course
(defn struct-parent
  [struct thing]
  (u/walk-find-path 
   #(= % thing) struct))

(defn infer-enums
  [s]
  (let [new-enums (atom [])
        ns
        (walk/postwalk
         (fn [thing]
           (if (and (map-entry? thing)
                    (= :enumerated (:type (second thing))))
             (let [[field fd] thing
                   values (:values fd)
                   kind (let [[_ path] (struct-parent s thing)]
                          (second (reverse path)))
                   enum (u/keyword-conc kind field "enum")]
               (swap! new-enums
                      conj
                      [enum {:values (zipmap (map (comp keyword clean-enum-value) values)
                                             (map humanize values))}])
               [field (-> fd
                          (assoc :type enum)
                          (dissoc :values))])
             thing))
         s)]
    (update ns :enums merge (into {} @new-enums))))



;;; in multitool now
(defn merge*
  [mcar & mcdr]
  (if (empty? mcdr)
    mcar
    (apply merge* (cons (u/merge-recursive mcar (first mcdr))
                        (rest mcdr)))))

#?
(:clj

(do
(declare read-schema)

(def ^:dynamic *dir* "")

(defn handle-include
  [s]
  (if (:include s)
    (let [merges (mapv #(read-schema (str *dir* "/" %)) ;TODO path resolution
                       (:include s))]
      (apply merge* (conj merges (dissoc s :include))))
    s))

;;; → Multitool
(defn path-dir
  [p]
  (second (re-find #"(.*)/(.*)" p)))

(defn read-schema
  [source]
  (prn :read-schema source)
  (binding [*dir* (path-dir source)]
    (-> source
        slurp
        edn/read-string
        handle-include
        infer-enums
        #_ validate-schema
        ))
  )))

;;; Schema introspection utilities

(defn kinds
  "Get all kind names from a schema"
  [schema]
  (keys (get schema :kinds)))

(defn enums
  "Get all enum names from a schema"
  [schema]
  (keys (:enums schema)))

(defn kind-def
  [schema kind]
  (assoc (get-in schema [:kinds kind]) :id kind))

(defn is-reference-type?
  "Check if a type is a reference to another kind"
  [type-name schema]
  (contains? (set (kinds schema)) type-name))

(defn is-enum-type?
  "Check if a type is an enum"
  [type-name schema]
  (contains? (set (enums schema)) type-name))

(u/defn-memoized inverse-fields
  [schema]
  (reduce-kv (fn [acc kind kdf]
               (reduce-kv (fn [acc field fdf]
                            (assoc-in acc [(:type fdf) field] #_kind (assoc fdf :type kind)))
                          acc
                          (:fields kdf)))
             {}
             schema))

;;; Inheritance utilities

(defn normalize-extends
  "Convert :extends to a vector for consistent handling.
   Accepts single keyword or vector of keywords."
  [extends]
  (cond
    (nil? extends) []
    (keyword? extends) [extends]
    (vector? extends) extends
    :else []))

(defn get-parents
  "Get direct parent kinds of a kind"
  [schema kind-name]
  (let [kind-def (get-in schema [:kinds kind-name])]
    (normalize-extends (:extends kind-def))))

(defn get-ancestors
  "Get all ancestors of a kind (transitive closure of parents).
   Returns a sequence in breadth-first order.
   Detects and throws on circular inheritance."
  [schema kind-name]
  (loop [to-visit [kind-name]
         visited #{}
         ancestors []]
    (if (empty? to-visit)
      ancestors
      (let [current (first to-visit)
            rest-to-visit (rest to-visit)]
        (if (contains? visited current)
          (if (= current kind-name)
            (throw (ex-info "Circular inheritance detected"
                           {:kind kind-name
                            :cycle (conj ancestors current)}))
            (recur rest-to-visit visited ancestors))
          (let [parents (get-parents schema current)
                new-ancestors (if (= current kind-name)
                               []
                               (conj ancestors current))]
            (recur (concat rest-to-visit parents)
                   (conj visited current)
                   new-ancestors)))))))

(defn deep-merge-field
  "Deep merge field definitions. Child field properties override parent,
   but :doc strings are concatenated."
  [parent-field child-field]
  (let [parent-doc (:doc parent-field)
        child-doc (:doc child-field)
        merged-doc (cond
                     (= parent-doc child-doc)
                     child-doc

                     (and parent-doc child-doc)
                     (str child-doc " (extends: " parent-doc ")")

                     child-doc child-doc
                     parent-doc parent-doc
                     :else nil)
        base-merge (merge parent-field child-field)]
    (if merged-doc
      (assoc base-merge :doc merged-doc)
      base-merge)))

(defn inherited-fields
  "Get all fields inherited from parent kinds with deep merge.
   Fields from later parents override earlier ones, and local fields override all."
  [schema kind-name]
  (let [ancestors (reverse (get-ancestors schema kind-name))] ; Reverse so closest ancestors override
    (reduce (fn [acc ancestor]
              (let [ancestor-fields (get-in schema [:kinds ancestor :fields])]
                (merge-with deep-merge-field acc ancestor-fields)))
            {}
            ancestors)))

(defn all-fields
  "Get all fields for a kind including inherited fields merged with local fields"
  [schema kind-name]
  (let [inherited (inherited-fields schema kind-name)
        local (get-in schema [:kinds kind-name :fields])]
    (merge-with deep-merge-field inherited local)))

;;; Inheritance validation

(defn validate-parent-exists
  "Check that all parent kinds exist in the schema"
  [schema kind-name]
  (let [parents (get-parents schema kind-name)
        all-kinds (set (keys (:kinds schema)))
        missing (remove all-kinds parents)]
    (when (seq missing)
      (throw (ex-info "Parent kind(s) do not exist"
                     {:kind kind-name
                      :missing-parents missing})))))

(defn validate-no-cycles
  "Check for circular inheritance in a kind"
  [schema kind-name]
  (try
    (get-ancestors schema kind-name)
    (catch #?(:clj Exception :cljs js/Error) e
      (throw (ex-info "Circular inheritance detected"
                     {:kind kind-name
                      :message #?(:clj (.getMessage e) :cljs (.-message e))})))))

(defn validate-inheritance
  "Validate all inheritance relationships in a schema"
  [schema]
  (doseq [kind-name (keys (:kinds schema))]
    (validate-parent-exists schema kind-name)
    (validate-no-cycles schema kind-name))
  schema)

