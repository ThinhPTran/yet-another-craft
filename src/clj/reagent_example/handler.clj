(ns reagent-example.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [org.httpkit.server :refer [with-channel on-close on-receive send!]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.reload :refer [wrap-reload]]
            [prone.middleware :refer [wrap-exceptions]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [environ.core :refer [env]]
            [reagent-example.util :as util]
            [clojure.tools.reader :as reader]))

(defonce state (atom nil))
(def current-time (atom nil))
(defonce users (atom {}))

;; (defn build-marine [parent]
;;   (if (>= @state-minerals marine-cost)
;;     (let [{:keys [user position]} (@state-entities parent)
;;           new-pos (util/select-spawn-point position {:x -64 :y -64})
;;           new-angle (rand 360)]
;;       (swap! state-minerals #(- % marine-cost))
;;       (add-entity (util/gen-id) (util/make-marine user new-pos new-angle)))))

;; (defn build-command-centre [user]
;;   (let [id (util/gen-id)
;;         pos (util/select-centre-pos @state-map)
;;         data (util/make-command-centre user pos)]
;;     (add-entity id data)
;;     (if (= user @state-user)
;;       (look-at pos))
;;     (build-marine id)
;;     (build-marine id)
;;     (build-marine id)
;;     [id data]))

(def home-page
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     (include-css (if (env :dev) "css/site.css" "css/site.min.css"))]
    [:body
     [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]]
     (include-js "js/app.js")]]))

(defn web-socket-handler [req]
  (with-channel req channel
    (let [username (get-in req [:params :name])]
      (println "socket opened")
      (println username)
      (send! channel (pr-str (util/make-state username)))
      (swap! users #(assoc % (pr-str channel) {}))
      (on-close channel (fn [status]
                          (swap! users #(dissoc % (pr-str channel)))
                          (println "socket closed")
                          ))
      (on-receive channel (fn [data]
                            (send! channel (pr-str {:state @state
                                                    :users @users
                                                    :echo data})))))))

(defroutes routes
  (GET "/" [] home-page)
  (GET "/:name" [] web-socket-handler)
  (resources "/")
  (not-found "Not Found"))

(def app
  (let [handler (wrap-defaults #'routes site-defaults)]
    (if (env :dev) (-> handler wrap-exceptions wrap-reload) handler)))
