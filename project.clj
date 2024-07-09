(defproject org.candelbio/alzabo "1.2.0"
  :description "Semantic schema format and tools, for Datomic and other uses."
  :url "http://github.com/candelbio/alzabo"
  :license {:name "Apache 2 License"
             :url "https://opensource.org/licenses/Apache-2.0"}
  :dependencies [;; Clojure 
                 [org.clojure/clojure "1.11.3"]
                 [hiccup "1.0.5"]
                 [clj-commons/clj-yaml "0.7.0"]
                 [me.raynes/fs "1.4.6"]
                 ;; Clojurescript
                 [org.clojure/clojurescript "1.10.520"]
                 [org.candelbio/multitool "0.1.5"]
                 [reagent  "0.8.1"]
                 [re-frame "0.10.6"]
                 ]
  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel  "0.5.16"]]
  :source-paths ["src/cljc" "src/clj" "src/cljs"] 
  :test-paths ["test/cljc" "test/clj" "test/cljs"]
  :aliases {"launch" ["do"
                      ["clean"]
                      ["run" "resources/candel-config.edn" "datomic"]
                      ["run" "resources/candel-config.edn" "documentation"]
                      ["cljsbuild" "once"]
                      ["run" "resources/candel-config.edn" "server" ]
                      ]}
  :main org.candelbio.alzabo.core
  :target-path "target/%s"
  :profiles {:library {:prep-tasks ["compile" ["cljsbuild" "once"]]
                       }
             :uberjar {:aot :all
                       :prep-tasks ["compile" ["cljsbuild" "once" "uberjar"]]
                       :omit-source true}
             :dev {:dependencies [[cider/piggieback "0.3.10"]
                                  [day8.re-frame/re-frame-10x "0.3.3"]
                                  [figwheel-sidecar "0.5.16"]
                                  ]
                   :cljsbuild
                   {:builds {:client {:figwheel     {:on-jsload "org.candelbio.alzabo.search.core/run"}
                                      :compiler     {:main "org.candelbio.alzabo.search.core"
                                                     :asset-path "js"
                                                     ;; for 10x debugger
                                                     :closure-defines {"re_frame.trace.trace_enabled_QMARK_" true}
                                                     :preloads [day8.re-frame-10x.preload]
                                                     :output-dir "resources/public/js"
                                                     :output-to  "resources/public/js/client.js"

                                                     :optimizations :none
                                                     :source-map true
                                                     :source-map-timestamp true}
                                      :source-paths ["src/clj" "src/cljs" "src/cljc"]
                                      }
                             }}
                   }
             :prod
             {:dependencies [[day8.re-frame/tracing-stubs "0.5.1"]]
              :cljsbuild
              {:builds {:client {
                                 :compiler     {:main "org.candelbio.alzabo.search.core"
                                                :asset-path "js"
                                                :closure-defines {goog.DEBUG false}
                                                :output-dir "resources/public/js"
                                                :output-to  "resources/public/js/client.js"
                                                :optimizations :advanced
                                                }
                                 :source-paths ["src/clj" "src/cljs" "src/cljc"]
                                 }
                        }}}
             }

  :clean-targets ^{:protect false} ["resources/public/js"
                                    "resources/schema"
                                    "resources/public/schema"]

  :cljsbuild {:builds {:client {:source-paths ["src/clj" "src/cljs" "src/cljc" "env/prod/cljs"]
                                :compiler     {:output-dir "resources/public/js"
                                               :output-to  "resources/public/js/client.js"}}
                       :uberjar {:source-paths ["src/clj" "src/cljs" "src/cljc" "env/prod/cljs"]
                                 :compiler     {:output-dir "resources/public/jsu"
                                                :output-to  "resources/public/jsu/client.js"
                                                :optimizations :advanced ;not working AFICT
                                                }}}} 

  :figwheel {
             :server-port 3452
             ;; Start an nREPL server into the running figwheel process
             :nrepl-port 7888
             }
  :resource-paths ["resources" "target/cljsbuild"]

  )
