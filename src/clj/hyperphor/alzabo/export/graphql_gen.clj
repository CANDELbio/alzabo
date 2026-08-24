(ns hyperphor.alzabo.export.graphql-gen
  (:require [clojure.string :as str]))

;;; Claude-generated

(defn edn-type->graphql-type
  "Convert EDN field type to GraphQL type"
  [field-def]
  (let [{:keys [type cardinality]} field-def
        base-type (case type
                    :string "String"
                    :numeric "Float"
                    :float "Float"
                    :number "Float"
                    :boolean "Boolean"
                    :enumerated "String" ; Will be handled as enum separately
                    (str (name type)))] ; Custom types
    (if (= cardinality :many)
      (str "[" base-type "]")
      base-type)))

(defn generate-field-definition
  "Generate a GraphQL field definition from EDN field"
  [field-name field-def]
  (let [graphql-type (edn-type->graphql-type field-def)
        required? (:required? field-def)
        doc (:doc field-def)
        final-type (if required? (str graphql-type "!") graphql-type)]
    (cond-> (str "  " (name field-name) ": " final-type)
      doc (str " # " doc))))

(defn generate-enum-definition
  "Generate GraphQL enum definition"
  [enum-name enum-def]
  (let [values (:values enum-def)]
    (str "enum " (str/capitalize (name enum-name)) " {\n"
         (->> values
              (map (fn [[k v]]
                     (str "  " (str/upper-case (name k))
                          (when v (str " # " v)))))
              (str/join "\n"))
         "\n}")))

(defn generate-type-definition
  "Generate GraphQL type definition from EDN kind"
  [kind-name kind-def enums]
  (let [type-name (str/capitalize (name kind-name))
        doc (:doc kind-def)
        fields (:fields kind-def)]
    (str (when doc (str "# " doc "\n"))
         "type " type-name " {\n"
         (->> fields
              (map (fn [[field-name field-def]]
                     (let [enum-type? (and (= (:type field-def) :enumerated)
                                           (:values field-def))
                           field-def' (if enum-type?
                                        (assoc field-def :type 
                                               (keyword (str (name kind-name) "_" (name field-name))))
                                        field-def)]
                       (generate-field-definition field-name field-def'))))
              (str/join "\n"))
         "\n}")))

(defn extract-field-enums
  "Extract enumerated fields from kinds to create enum definitions"
  [kinds]
  (reduce
   (fn [acc [kind-name kind-def]]
     (reduce
      (fn [acc' [field-name field-def]]
        (if (and (= (:type field-def) :enumerated)
                 (:values field-def))
          (assoc acc' 
                 (keyword (str (name kind-name) "_" (name field-name)))
                 {:values (zipmap (:values field-def) (:values field-def))})
          acc'))
      acc
      (:fields kind-def)))
   {}
   kinds))

(defn generate-graphql-schema
  "Generate complete GraphQL schema from EDN schema"
  [edn-schema]
  (let [{:keys [kinds enums title version]} edn-schema
        field-enums (extract-field-enums kinds)
        all-enums (merge enums field-enums)]
    
    (str "# " title "\n"
         "# Version: " version "\n\n"
         
         ;; Generate enums
         (->> all-enums
              (map (fn [[enum-name enum-def]]
                     (generate-enum-definition enum-name enum-def)))
              (str/join "\n\n"))
         
         "\n\n"
         
         ;; Generate types
         (->> kinds
              (map (fn [[kind-name kind-def]]
                     (generate-type-definition kind-name kind-def all-enums)))
              (str/join "\n\n"))
         
         "\n\n"
         
         ;; Generate root Query type
         "type Query {\n"
         (->> kinds
              keys
              (map (fn [kind-name]
                     (let [type-name (str/capitalize (name kind-name))]
                       (str "  " (name kind-name) "s: [" type-name "]\n"
                            "  " (name kind-name) "(id: ID!): " type-name))))
              (str/join "\n"))
         "\n}")))

;; Example usage with your schema
(def radiohead-schema
  {:title "RADIOHEAD Data Portal Object Model"
   :version "0.1"
   :kinds
   {:subject
    {:doc "Subjects aka patients, participants. All clinical data goes here"
     :fields {:ir-ae-productive-cough {:type :boolean, :doc "irAE productive cough"}
              :days-guillain-barre-syndrome {:min 0, :max 64, :type :numeric, :doc "days guillain-barre syndrome"}
              :subject-age {:min 25, :max 89, :type :numeric, :doc "subject age"}
              :subject-sex {:type :enumerated, :values ["male" "female"], :doc "subject sex"}
              :cancer {:type :enumerated
                      :values ["Renal cell carcinoma (RCC)"
                              "Urothelial carcinoma (UC)"
                              "Hepatocellular carcinoma"
                              "Melanoma"
                              "Non-small cell lung cancer (NSCLC)"]
                      :doc "Cancer"}}}
    
    :sample
    {:doc "A physical sample for a particular patient and timepoint"
     :fields {:id {:type :string :unique? true :required? true}
              :subject {:type :subject :required? true}
              :timepoint {:type :timepoint :required? true}
              :barcode {:type :string}
              :material {:type :material :required? true :doc "biological material (eg blood, serum)"}}}
    
    :datum
    {:doc "The results of a particular assay for a particular sample"
     :fields {:assay-type {:type :assay-type}
              :sample {:type :sample}}}}
   
   :enums
   {:assay_type {:values {:ctDNA "Circulating DNA"
                         :flow-cytomtery "Flow Cytometry"
                         :RNA-seq "RNAseq"
                         :WES "Whole Exome Sequencing"}}
    :timepoint {:values {:pre-treatment "Pre-treatment"
                        :on-treatment "On Treatment"
                        :irAE "irAE"
                        :follow-up "Follow-up"}}}})

;; Generate the GraphQL schema
(defn -main []
  (println (generate-graphql-schema radiohead-schema)))

;; You can also use it programmatically:
;; (def graphql-schema (generate-graphql-schema your-edn-schema))
