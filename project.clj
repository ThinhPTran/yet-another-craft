(defproject reagent-example "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122" :scope "provided"]
                 [ring-server "0.4.0"]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [reagent "0.5.1"]
                 [reagent-forms "0.5.11"]
                 [reagent-utils "0.1.5"]
                 [prone "0.8.2"]
                 [compojure "1.4.0"]
                 [hiccup "1.0.5"]
                 [environ "1.0.1"]
                 [secretary "1.2.3"]]

  :plugins [[lein-environ "1.0.1"]
            [refactor-nrepl "2.0.0-SNAPSHOT"]
            [lein-asset-minifier "0.2.2"]
            [cider/cider-nrepl "0.10.0-SNAPSHOT"]]

  :ring {:handler reagent-example.handler/app
         :uberwar-name "reagent-example.war"}

  :min-lein-version "2.5.0"

  :uberjar-name "reagent-example.jar"

  :main reagent-example.server

  :clean-targets ^{:protect false} [:target-path
                                    [:cljsbuild :builds :app :compiler :output-dir]
                                    [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj" "src/cljc"]

  :minify-assets
  {:assets
   {"resources/public/css/site.min.css" "resources/public/css/site.css"}}

  :cljsbuild {:builds {:app {:source-paths ["src/cljs" "src/cljc"]
                             :figwheel {:websocket-host "192.168.1.66"}
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :asset-path   "js/out"
                                        :optimizations :none
                                        :pretty-print  true}}}}

  :figwheel {:http-server-root "public"
             :server-port 3449
             :nrepl-port 7002
             :css-dirs ["resources/public/css"]
             :ring-handler reagent-example.handler/app}

  :profiles {:dev {:repl-options {:init-ns reagent-example.repl
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl
                                                     cider.nrepl.middleware.apropos/wrap-apropos
                                                     cider.nrepl.middleware.classpath/wrap-classpath
                                                     cider.nrepl.middleware.complete/wrap-complete
                                                     cider.nrepl.middleware.info/wrap-info
                                                     cider.nrepl.middleware.inspect/wrap-inspect
                                                     cider.nrepl.middleware.macroexpand/wrap-macroexpand
                                                     cider.nrepl.middleware.ns/wrap-ns
                                                     cider.nrepl.middleware.resource/wrap-resource
                                                     cider.nrepl.middleware.stacktrace/wrap-stacktrace
                                                     cider.nrepl.middleware.test/wrap-test
                                                     cider.nrepl.middleware.trace/wrap-trace
                                                     cider.nrepl.middleware.undef/wrap-undef]}

                   :dependencies [[ring/ring-mock "0.3.0"]
                                  [ring/ring-devel "1.4.0"]
                                  [lein-figwheel "0.4.0"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [org.clojure/tools.nrepl "0.2.11"]
                                  [pjstadig/humane-test-output "0.7.0"]]

                   :source-paths ["env/dev/clj"]
                   :plugins [[lein-figwheel "0.4.0"]
                             [lein-cljsbuild "1.0.6"]]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :env {:dev true}

                   :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]
                                              :figwheel {:websocket-host "192.168.1.66"}
                                              :compiler {:main "reagent-example.dev"
                                                         :source-map true}}}}}

             :uberjar {:hooks [leiningen.cljsbuild minify-assets.plugin/hooks]
                       :env {:production true}
                       :aot :all
                       :omit-source true
                       :cljsbuild {:jar true
                                   :builds {:app
                                            {:source-paths ["env/prod/cljs"]
                                             :compiler
                                             {:optimizations :advanced
                                              :pretty-print false}}}}}})
