(ns org.parkerici.alzabo.config
  (:require [clojure.edn :as edn]
            [org.parkerici.multitool.core :as u])
  )

(def the-config (atom nil))

(defn set-config!
  [file]
  (reset! the-config (if file
                       (edn/read-string (slurp file))
                       nil)))

(defn set!
  [att value]
  (swap! the-config assoc att value))

(defn config
  ([key] (get @the-config key))
  ([] @the-config ))

;;; TODO document or default config vars here

;;; → multitool – change to deal with keyword bindings
(defn expand-template-string
  "Template is a string containing {foo} elements, which get replaced by corresponding values from bindings"
  [template bindings]
  (let [matches (->> (re-seq u/template-regex template) 
                     (map (fn [[match key]]
                            [match (or (bindings key) (bindings (keyword key)) "")])))]
    (reduce (fn [s [match key]]
              (clojure.string/replace s (u/re-pattern-literal match) (str key)))
            template matches)))

(defn output-path
  [filename]
  (str (expand-template-string (config :output-path) config)
       filename))





