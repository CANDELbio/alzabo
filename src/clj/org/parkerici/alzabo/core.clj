(ns org.parkerici.alzabo.core
  (:require [org.parkerici.alzabo.candel :as candel]
            [org.parkerici.alzabo.schema :as schema]
            [org.parkerici.alzabo.config :as config]
            [org.parkerici.alzabo.html :as html]
            [org.parkerici.alzabo.output :as output]
            [org.parkerici.alzabo.datomic :as datomic]
            [org.parkerici.multitool.core :as u])
  (:gen-class))

;;; Note: CLI use is somewhat deprecated; it's expected that Alzabo will be used
;;; more as a library.

(defn- browse-file
  [file]
  (.browse (java.awt.Desktop/getDesktop)
           (.toURI (java.io.File. file))))

(defn- schema
  []
  (let [schema
        (if (= (config/config :source) :candel)
          (candel/read-schema)
          (schema/read-schema (config/config :source)))]
    (config/set! :version (:version schema))
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
  [_ _] 
  (let [schema (schema)]
    (when (= (config/config :source) :candel)
      ;; write out derived Alzabo schemas
      (write-alzabo schema))
    (html/schema->html schema)))

(defmethod do-command :datomic
  [_ _]
  (let [schema (schema)]
    (write-alzabo schema)
    (output/write-schema (datomic/datomic-schema schema)
                         (config/output-path "datomic-schema.edn"))
    (output/write-schema (candel/metamodel schema)
                         (config/output-path "metamodel.edn"))

    ))

;;; Split out for testing
(defn -main-guts
  [config command args]
  (config/set-config! config)
  (do-command command args)
  )
  
;;; Note: this isn't currently used, all the params are in config
(defn keywordize-keys
  [m]
  (u/map-keys read-string m))

(defn -main
  [config command & args]
  (-main-guts config command (keywordize-keys (apply hash-map args)))
  (System/exit 0))
