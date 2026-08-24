(ns hyperphor.alzabo.html
  (:require [hyperphor.alzabo.schema :as schema]
            [hyperphor.multitool.core :as u]
            [clojure.string :as s]
            [clojure.pprint :as pp]
            [me.raynes.fs :as fs]
            [inflections.core :as inflect]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell])
  (:import [java.nio.file Files Path LinkOption]
           [java.nio.file.attribute FileAttribute])
  (:use [hiccup.core]))

;;; Rendering options that used to live in config; now read from the schema
;;; itself (with these as fallback defaults) since they're presentation
;;; details of a particular schema, not deployment config.
(def default-graph-options
  {:edge-labels? :minimal
   :width 100
   :height 64
   :orientation :horizontal
   :categories {:default {:color "#a9cce3"}}})

(defn- with-graph-defaults
  [schema]
  (merge default-graph-options schema))

(defn- kind-url
  [kind]
  (str (name kind) ".html"))

(defn- kind-link
  [kind]
  (html [:a.kind {:href (kind-url kind)}
         kind]))

;;; TODO links in docs
(defn- kind-html
  [kind]
  (if (keyword? kind)
    (if (get schema/primitives kind)
      (name kind)
      (kind-link kind))
    ;; tuple
    [:span "["
     (interpose
      " "
      (for [k kind]
        (kind-html k)))
     "]"]))

(defn field-spec-html
  [{:keys [type min max] :as fieldprops}]
  (cond (schema/numeric-primitives type)
        (str (name type)
             (when min
               (str [min max])))
        :else
        (kind-html type)))
        

(def primitives #{:long :float :string :boolean :instant :keyword}) ; :ref

(defn- linkify
  [s]
  (when s 
    (s/replace s #"(http(s|)\:\S*)" "<a href=\"$1\">$1</a>")))

;;; can't believe this isn't built into hiccup
(defn- style-arg
  [m]
  (s/join (map (fn [[p v]] (format "%s: %s;" (name p) v)) m)))

(defn boold
  [boolean]
  (if boolean "yes" ""))

;;; TODO someday these might want to be customizable in config
(def kind-metadata-columns
  [{:display (fn [fieldprops] [:span {:style (style-arg {:white-space "nowrap"})} (:id fieldprops)])
    :heading "attribute"}
   {:display field-spec-html
    :heading "type"}
   {:display :cardinality
    :heading "cardinality"}
   {:display (comp boold :unique?)
    :heading "unique?"}
   {:display (comp boold :required?)
    :heading "required?"}
   {:display (fn [fieldprops] (linkify (:doc fieldprops)))
    :heading "doc"}
   {:display (fn [fieldprops]
               (when-let [examples (seq (:examples fieldprops))]
                 [:span {:class "examples"} (s/join ", " examples)]))
    :heading "examples"}])

(def kind-inverse-columns
  [{:display field-spec-html
    :heading "from"}
   {:display (fn [fieldprops] [:span {:style (style-arg {:white-space "nowrap"})} (:id fieldprops)])
    :heading "attribute"}
   {:display :cardinality
    :heading "cardinality"}
   {:display (comp boold :required?)
    :heading "required?"}
   {:display (fn [fieldprops] (linkify (:doc fieldprops)))
    :heading "doc"}])

(defn- field->html
  [field props columns]
  (html
   [:tr
    (for [col columns]
      [:td ((:display col) (assoc props :id field))])]))

(defn- table-headings
  [columns]
  [:tr (for [col columns]
         [:th (:heading col)])])

(defn- backlink
  []
  [:a {:href "index.html"} "← schema"])

;;; Schema is actually just the kinds structure
(defn- kind->html
  [kind raw-schema]
  (let [kind-def (get-in raw-schema [:kinds kind])
        {:keys [unique-id label fields doc extends]} kind-def
        parents (schema/get-parents raw-schema kind)
        inherited (schema/inherited-fields raw-schema kind)
        all-fields (schema/all-fields raw-schema kind)]
    (html
     [:div.container
      (backlink)
      [:h1 (name kind)]
      (when doc
        [:div {:class "kind_doc"} (linkify doc)])

      ;; Show inheritance
      (when (seq parents)
        [:div {:style "margin: 1em 0; padding: 0.5em; background-color: #f5f5f5; border-left: 3px solid #4CAF50;"}
         [:b "Extends: "]
         (interpose ", " (map kind-link parents))])

      [:h3 "Fields"]
      [:table {:class "table"}
       (table-headings kind-metadata-columns)
       (for [[field props] (into (sorted-map) all-fields)]
         (let [is-inherited (contains? inherited field)
               props-with-note (if is-inherited
                                 (update props :doc #(str (or % "") " [inherited]"))
                                 props)]
           (field->html field props-with-note kind-metadata-columns)))]

      [:h3 "Inverse Relations"]
      [:table {:class "table"}
       (table-headings kind-inverse-columns)
       (for [[field props] (into (sorted-map) (get (schema/inverse-fields raw-schema) kind))]
         (field->html field props kind-inverse-columns))]

      (when unique-id
        [:div
         [:b "Unique ID"] ": " (name unique-id)])
      (when label
        [:div
         [:b "Label"] ": " (name label)])]
     )))

(defn- enum->html
  [enum {:keys [values doc]}]
  (html
   [:div.container
    (backlink)
    [:h1 (name enum)]
    [:span doc]
    [:table {:class "table"}
     [:tr [:th "values"] [:th "doc"]]
     (for [[v doc] values]
       (html [:tr [:td v] [:td doc]]))]]))

(defn- clear-directory
  [d]
  (fs/delete-dir d LinkOption/NOFOLLOW_LINKS)
  (fs/mkdirs d))

(defn output-file
  [output-path file]
  (str (fs/file output-path file)))

(def alzabo-link "https://github.com/hyperphor/alzabo")

(defn html-out
  [output-path file title the-html]
  (prn :writing (output-file output-path file))
  (spit (output-file output-path file)
        (html
            ;; should be a template I suppose but this was faster
            [:html
             [:head
              [:title title]
              [:meta {:charset "UTF-8"}]   ;TODO was UTF-16, which broke client.js...why was it that way?
              [:link {:href "https://fonts.googleapis.com/css?family=Lato:400,700"
                      :rel "stylesheet"}]


              [:link {:href "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"
                      :rel "stylesheet"
                      :integrity "sha384-QWTKZyjpPEjISv5WaRU9OFeRpok6YctnYmDr5pNlyT2bRjXh0JMhjY6hW+ALEwIH"
                      :crossorigin "anonymous"}]
              [:link {:rel "stylesheet"
                      :href "alzabo.css"}]]
             [:body
              [:div
               the-html
               [:div.footer "Generated by " [:a {:href alzabo-link} "Alzabo"]]]]])))


;; yes this should be in css.
(defn- header-style
  [color]
  (style-arg
   {:background color
    :padding-left "6px"
    }))

(defn enum-samples
  [enum]
  (str (s/join ", " (map second (take 3 (:values enum))))
       (when (> (count (:values enum)) 3) "...")))

(defn page-html
  [title body search?]
  (html
      [:div.container
       [:div.header
        [:h1 title]
        (when search? [:div#app])
        [:span "Generated by " [:a {:href alzabo-link} "Alzabo"]]]
       body]))
  

(defn- index->html
  [{:keys [kinds enums version title categories explanation] :as schema} tag-version output-path]
  (let [categories (or categories (:categories default-graph-options)) ;TODO kludge
        groups (group-by #(get % :category :default) (vals (u/self-label :id kinds)))]
    (html
        [:div.container                 ;TODO use page-html
         [:div.header
          [:h1 title " " version]
          [:div#app]
          [:span "Generated by " [:a {:href alzabo-link} "Alzabo"]]]
         (when-let [exp explanation]
           (vec (cons :div.explanation exp))
           )
         [:div.row
          [:div.py-5
           [:img {:src "schema.dot.svg"
                  :usemap "#schema"}]
           (slurp (output-file output-path "schema.dot.cmapx"))
           ]]
         [:div.row
          [:div.m-2
           [:h2 "Entities"]                ;aka Kinds, I suppose this should be configurable
           (for [category-name (keys categories)]
             (let [kinds (category-name groups)
                   category (category-name categories)]
               [:div
                (when (> (count categories) 1)
                  [:h3 {:style (header-style (:color category))}
                   (or (:label category)
                       (inflect/titleize category-name))])
                [:table.table.table-sm
                 (for [kind (sort-by :id kinds)]
                   [:tr
                    [:th (kind-html (:id kind))]
                    [:td (linkify (:doc kind))]]
                   )]
                ]))]]
         [:div.row
          [:div.m-2
           [:h2 "Enums"]
           [:table.table-sm
            (for [enum (sort (keys enums))]
              [:tr
               [:th (kind-html enum)]
               [:td (linkify (:doc (get enums enum)))]
               [:td (enum-samples (get enums enum))]
               ]
              )]]]
         ;; Pass the schema to clojurescript widget inside an invisible div
         ;; See hyperphor.alzabo.search.core/get-schema
         [:div#aschema {:style (style-arg {:display "none"})}
          (str schema)]
         [:script {:src "client.js"}]
         [:script "window.onload = function() { hyperphor.alzabo.search.core.run(); }"]
         ]
      )))

(defn- kind-relations
  "This generates the list of edges in the graph. Given a kind, returns a list of [relation target cardinality] pairs. Expands tuples"
  [kind {:keys [kinds enums]}]
  (filter (fn [[_ type _]]
            (get kinds type))
          (mapcat (fn [[fname {:keys [cardinality type] :as field-def}]]
                    (if (vector? type)  ; handle tuple types
                      (map (fn [tuple-component idx]
                             [(keyword (str (name fname) idx)) tuple-component cardinality])
                           type (range))
                      (list [fname type cardinality])))
                  (get-in kinds [kind :fields]))))

(defn- sh-errchecked
  [& args]
  (let [res (apply shell/sh args)]
    (when-not (= (:exit res) 0)
      (throw (Exception. (str "Bad result from shell" res))))
    res))

;;; Requires installation of graphviz ('brew install graphviz' on Mac)
(def dot-command "dot")

(def graph-font "Helvetica")

;;; TODO if this gets any more complex, consider replacing with https://github.com/daveray/dorothy
(defn- write-graphviz
  [schema dot-file]
  (let [{:keys [kinds enums categories orientation width height edge-labels?]} (with-graph-defaults schema)
        clean (fn [kind] (s/replace (name kind) \- \_))
        attributes
        (fn [m & [sep]]
          ;; For some reason graph attributes need a different separator
          (s/join (or sep ",") (map (fn [[k v]] (format "%s=%s" (name k) (pr-str v))) m)))]
    (println "Writing " dot-file)
    (with-open [wrtr (clojure.java.io/writer dot-file)]
      (binding [*out* wrtr]
        (println "digraph schema {")
        (println (attributes
                  {:rankdir (case orientation
                              :horizontal "LR"
                              :vertical "TB")
                   :size (str height "," width)
                   }
                  ";"))
        (doseq [kind (keys kinds)]
          (let [{:keys [doc category] :or {category :default}} (get-in schema [:kinds kind])]
            (println (format "%s [%s];"
                             (clean kind)
                             (attributes {:URL (kind-url kind)
                                          :label (name kind)
                                          :tooltip (or doc (name kind))
                                          :style "filled"
                                          :fillcolor (get-in categories [category :color])
                                          :fontname graph-font}))))
          (doseq [[label ref cardinality] (kind-relations kind schema)]
            (println (format "%s -> %s [%s];"
                             (clean kind)
                             (clean ref)
                             (attributes
                              (merge
                               {:arrowhead (if (= cardinality :many) "diamond" "normal")} ;"crow" is better semantically but looks bad on ovals.
                               (if (case edge-labels?
                                     :minimal (not (or (= label ref)
                                                       (= (name label) (inflect/plural ref))))
                                     true true
                                     :else false)
                                 {:fontname graph-font
                                  :label (name label)})))
                             )))
        ;; Add inheritance edges with dashed lines (child -> parent)
          (doseq [parent (schema/get-parents schema kind)]
            (println (format "%s -> %s [%s];"
                             (clean kind) ;TODO might want to swap these
                             (clean parent)
                             (attributes {:style "dashed"
                                          :arrowhead "empty"
                                          :color "#4CAF50"
                                          :penwidth "2.0"})))))

        (println "}"))
      (println "Generating .svg")
      (sh-errchecked
       dot-command
       dot-file
       "-Tsvg"
       "-O"
       "-Tcmapx"
       "-O"
       )
      )))

;;; the fs/ symlink command won't do the right thing
(defn- make-link
  [path target]
  (Files/createSymbolicLink
   (.toPath (io/file path))
   (.toPath (io/file target))
   (make-array FileAttribute 0)))

(defn schema->html
  "Generate HTML docs for a schema, including .svg and related files, writing them to output-path."
  [{:keys [kinds enums version title] :as schema} output-path]

  (clear-directory output-path)
  ;; Write out the schema itself – used by autocomplete, enflame, etc
  (with-open [o (clojure.java.io/writer (output-file output-path "schema.edn"))]
    (pp/pprint schema o))
  (doseq [kind (keys kinds)]
    (html-out output-path
              (str (name kind) ".html")
              (format "%s - %s - Alzabo" (name kind) title)
              (kind->html kind schema)
             ))
  (doseq [enum (keys enums)]
    (html-out output-path
              (str (name enum) ".html")
              (format "%s - %s - Alzabo" (name enum) title)
              (enum->html enum (get enums enum))
              ))
  (write-graphviz schema (output-file output-path "schema.dot"))
  (html-out output-path
            "index.html"
            (format "%s - Alzabo" title)
            (index->html schema version output-path)
            #_ version
            )
  #_ (make-link (output-file output-path "js") "../../js") ;argh. Necessary apparently, net infrastructure no longer lets .. work
  #_ (make-link (output-file output-path "alzabo.css") "../../alzabo.css")
  (io/copy (io/reader (io/resource "public/alzabo.css"))
           (io/file (output-file output-path "alzabo.css")))
  (io/copy (io/reader (io/resource "public/jsu/client.js")) ;Copy the uberjar version, which is single-file, so might actually work
           (io/file (output-file output-path "client.js")))
  nil
  )


