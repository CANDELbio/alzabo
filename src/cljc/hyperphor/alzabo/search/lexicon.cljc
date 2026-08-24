(ns hyperphor.alzabo.search.lexicon
  (:require [clojure.string :as str]
            [hyperphor.multitool.nlp :as nlp]))
  
(defn add-def-word [dict word def]
  (let [word (str/lower-case word)]
    (assoc dict word (cons def (get dict word '())))))

(defn add-def-key [dict key def]
  (reduce (fn [dict word]
            (add-def-word dict word def))
          dict
          (str/split (name key) #"-")))

(defn add-def-text [dict text def]
  (if text
    (reduce (fn [dict word]
              (add-def-word dict word def))
            dict
            (nlp/tokens text))
    dict))

(defn kind-dict [kinds dict]
  (reduce (fn [dict [kindname kinddef]]
            (reduce (fn [dict prop]
                      (-> dict
                          (add-def-key prop (list 'prop kindname prop))
                          (add-def-text (get-in kinddef [:fields prop :doc]) (list 'prop kindname prop))))
                    (add-def-key dict kindname (list 'kind kindname))
                    (keys (:fields kinddef))))
          dict
          kinds))

(defn enum-dict [enums dict]
  (reduce (fn [dict [enum {:keys [values]}]]
            (reduce (fn [dict [v doc]]
                      (-> dict
                          (add-def-key v (list 'enum-value enum v doc))
                          (add-def-text doc (list 'enum-value enum v doc)))) 
                    (add-def-key dict enum (list 'enum enum))
                    values))
          dict
          enums))

(defn merged-dict [{:keys [kinds enums]}]
  (->> {}
       (kind-dict kinds)
       (enum-dict enums)))

;;; This is a slow algorithm; if the lexicon gets bigger, use a trie or similar data structure
(defn lookup [prefix lexicon]
  (if (empty? prefix)
    []
    (let [prefix (str/lower-case prefix)
          raw (filter (fn [[word defs]]
                        (= prefix (subs word 0 (min (count word) (count prefix)))))
                      lexicon)]
      ;; The sort works because lexical order accidentally happens to be a good semantic order...
      (take 20 (sort-by first (distinct (mapcat identity (vals raw))))))))
