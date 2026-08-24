(ns hyperphor.alzabo.core
  (:require [hyperphor.alzabo.import.candel :as candel]
            [hyperphor.alzabo.schema :as schema]
            [hyperphor.alzabo.config :as config]
            [hyperphor.alzabo.html :as html]
            [hyperphor.alzabo.output :as output]
            [hyperphor.alzabo.export.datomic :as datomic]
            [hyperphor.alzabo.datagen :as datagen]
            [hyperphor.alzabo.schema-gen-llm :as sgl]
            [hyperphor.multitool.core :as u]
            [hyperphor.multitool.cljcore :as ju]
            [me.raynes.fs :as fs]
            [clojure.data.json :as json]
            )
  (:gen-class))

;;; Note: CLI use is somewhat deprecated; it's expected that Alzabo will be used
;;; more as a library.

(defn- browse-file
  [file]
  (.browse (java.awt.Desktop/getDesktop)
           (.toURI (java.io.File. file))))

;;; :candel special casing removed
(defn- schema
  [schema-file]
  (let [schema-file (or schema-file (config/realize-path (config/config :source)))
        schema (schema/read-schema schema-file)]
    (config/set! :version (:version schema)) ;?
    schema))

;;; New config-file machinery

(defmulti do-command (fn [command args] (keyword command)))

(defmethod do-command :server
  [_ _]
  (schema)                              ;sets version
  (browse-file (config/output-path "index.html")))

(defn write-alzabo
  [schema]
  (output/write-schema schema (config/output-path "alzabo-schema.edn"))) 

(defmethod do-command :documentation
  [_ {:keys [schema-file]}]
  (let [output-path (config/output-path "")]
    (if (= (config/config :source) :candel)
      ;; write out derived Alzabo schemas
      (let [schema (candel/produce-schema)]
        (write-alzabo schema)
        (html/schema->html schema output-path))
      (let [schema (schema schema-file)]
        (html/schema->html schema output-path)))))

(defmethod do-command :datomic
  [_ _]
  (let [schema (schema nil)]
    (write-alzabo schema)
    (output/write-schema (datomic/datomic-schema schema)
                         (config/output-path "datomic-schema.edn"))
    ;; ?
    #_
    (output/write-schema (candel/metamodel schema)
                         (config/output-path "metamodel.edn"))

    ))

(defmethod do-command :generate-data
  [_ args]
  (let [schema-data (schema)
        entity-counts (or (:entity-counts args) {})
        llm-enabled? (get args :llm-enabled? true)
        output-format (get args :output-format "edn")]
    (println "Generating sample data for schema...")
    (let [generated-data (datagen/generate-sample-data schema-data
                                                       :entity-counts entity-counts
                                                       :llm-enabled? llm-enabled?)]
      (case output-format
        "edn" (output/write-schema generated-data (config/output-path "sample-data.edn"))
        "json" (spit (config/output-path "sample-data.json")
                     (json/write-str generated-data))
        (println "Unknown output format:" output-format))
      (println (str "Generated sample data written to "
                    (config/output-path (str "sample-data." output-format)))))))

;;; Split out for testing
(defn main-guts
  [config command]
  (config/set-config! config)
  (do-command command {})
  )
  
;;; Note: this isn't currently used, all the params are in config
(defn keywordize-keys
  [m]
  (u/map-keys read-string m))

(defn -main
  [config command & args]
  (main-guts config command)
  (System/exit 0))

(defn main
  [config command & args]
  (main-guts config command)
  )

;;; Build and show schema doc
(defn demo
  [schema-file sname]
  (let [output-dir (u/tx "resources/public/schema/{{sname}}/")
        base (ju/wd)
        config {:source schema-file
                :output-path output-dir
                :edge-labels? true
                }]
    (config/set-config! config)
    (do-command :documentation {:schema-file schema-file})
    (ju/open-url (u/tx "file://{{base}}/{{output-dir}}index.html"))))

;;; Generaste a schema from a domain description (and display it)
(defn full-demo
  [domain sname & [extra]]
  (let [schema (sgl/sgen domain extra)
        schema-file (u/tx "resources/generated/{{sname}}.edn")]
    (output/write-schema schema schema-file)
    (demo schema-file sname))) 


(comment 
  (demo "resources/generated/rockets.edn" "rockets")
  (full-demo "scientists" "scientists"))


(comment
  (doseq [f (ju/content-files "resources/generated/")]
    (demo (str f) (fs/base-name f))))

(defn demo-entities
  [schema-file kind extra]
  (let [schema (schema/read-schema schema-file)]
    (hyperphor.alzabo.datagen/generate-entities
     kind schema :kind-modifier extra)))

(comment
  (demo-entities "/opt/mt/repos/hyperphor/alzabo/resources/generated/drugs.edn" :PyschoactiveDrug "fictional"))


;;; → multitool or way, haven't I written this a million times already?
;;; TODO needs moar options
(defn- column-label
  [c]
  (if (map? c)
    (or (get c :label)
        (name (get c :key)))
    (name c)))

(defn- column-row-cell
  [c row]
  (if (:url c)
    [:a {:href (u/expand-template (:url c) row)}
     (column-row-cell (dissoc c :url) row)]
    (get row (or (get c :key) c))))        ;TODO value munging

(defn table
  [data columns]
  `[:table.table {:class "table"}
    [:tbody
     [:tr
      ~@(for [c columns]
          [:th (column-label c)])]                          ;todo
     ~@(for [r data]
         `[:tr ~@(map (fn [c] [:td (column-row-cell c r)]) columns)])
     ]])

(defn directory
  []
  (let [dirs (filter fs/directory? (fs/list-dir "resources/public/schema"))
        schemas (map #(assoc (schema/read-schema (str % "/schema.edn")) :file (fs/base-name %))
                     dirs)]
    (html/html-out
     "resources/public/schema/"
     "directory.html"
     "Directory of generared schemas"
     (html/page-html
      "Schema Directory"
      (table schemas [{:key :file :url  "{{file}}/index.html"} :title :description]) false))))
