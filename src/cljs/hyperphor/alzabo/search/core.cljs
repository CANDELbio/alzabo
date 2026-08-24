(ns hyperphor.alzabo.search.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as rf]
            [clojure.reader :as reader]
            [hyperphor.alzabo.search.lexicon :as lex]
            ))

;; Get the schema from an invisible div on the page
(defn get-schema []
  (clojure.reader/read-string (.-textContent (.getElementById js/document "aschema"))))

(rf/reg-event-db              ;; sets up initial application state
 :initialize                 ;; usage:  (dispatch [:initialize])
 (fn [_ _]                   ;; the two parameters are not important here, so use _
   (let [schema (get-schema)]
     {:user-string ""
      :schema schema
      :lexicon (lex/merged-dict schema)
      :choices []
      })))

(rf/reg-event-db
 :user-string-change
 (fn [db [_ user-string]]
   (assoc db
          :user-string user-string
          :choices (lex/lookup user-string @(rf/subscribe [:lexicon]))
          )))

(rf/reg-sub
  :user-string
  (fn [db _]  
    (:user-string db)))

;;; Don't really need to have this or schema in state, they don't change
(rf/reg-sub
  :lexicon
  (fn [db _]  
    (:lexicon db)))

(rf/reg-sub
  :schema
  (fn [db _]  
    (:schema db)))

(rf/reg-sub
  :choices
  (fn [db _]  
    (:choices db)))

(defn re-substitute
  "Modeled on re-seq, but returns a list based on substrings, with subfn applied to substrings that match re"
  [re s subfn]
  (let [match-data (re-find re s)
        match-idx (.search s re)
        match-str (if (coll? match-data) (first match-data) match-data)
        post-idx (+ match-idx (max 1 (count match-str)))
        post-match (subs s post-idx)]
    (if match-data
      (lazy-seq (cons (subs s 0 match-idx)
                      (cons (subfn match-data)
                            (when (<= post-idx (count s))
                              (re-substitute re post-match subfn)))))
      (list post-match))))

(defn highlight-name
  [name typed]
  (when name
    (into [] (cons :span (re-substitute (re-pattern (str "(?i)\\b" typed)) name (fn [match] [:b match] ))))))

;;; Also used for enum links, so slightly misnamed
(defn kind-link
  [kind & [body]]
  [:a {:href (str "" (name kind) ".html")
       :target "schema"}
   (or body
       (name kind))])

(defn render-item
  [[type & params] user-string]
  (case type
    kind [:div "Kind " (kind-link (first params) (highlight-name (name (first params)) user-string))]
    prop [:div "Property " (highlight-name (name (second params)) user-string) " of kind "
          (kind-link (first params))
          [:div.doc (highlight-name (get-in @(rf/subscribe [:schema]) [:kinds (first params) :fields (second params) :doc]) user-string)]]
    enum [:div "Enumeration " (kind-link (first params) (highlight-name (name (first params)) user-string))]
    enum-value [:div "Value "
                (highlight-name (name (second params)) user-string)
                " (" (highlight-name (nth params 2) user-string)
                ") of enumeration "
                (kind-link (first params))]))

(defn ui
  []
  [:div {:style {:padding "10px"}}
   [:div
    "search: "
    [:input {:value @(rf/subscribe [:user-string])
             :on-change (fn [e]
                          (rf/dispatch
                           [:user-string-change (-> e .-target .-value)]))}]

    ]
   (let [user-string @(rf/subscribe [:user-string])
         choices @(rf/subscribe [:choices]) ]

     [:div.popup
      [:div.popuptext {:style {:visibility (if (empty? choices) "hidden" "visible")}}
       (if (empty? choices)
         [:div [:h3 "No results"]]
         [:table
          [:tbody
          ;; TODO sorting
          (for [def choices]
            ^{:key def}
            [:tr
             [:td (render-item def user-string)]])]])]])
   ]
  ) 

(defn ^:export run
  []
  (rf/dispatch-sync [:initialize])
  (reagent/render [ui] ;; mount the application's ui into '<div id="app" />'
    (js/document.getElementById "app")))
