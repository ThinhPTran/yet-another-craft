(ns yet-another-craft.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [org.httpkit.server :refer [with-channel on-close on-receive send!]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.reload :refer [wrap-reload]]
            [prone.middleware :refer [wrap-exceptions]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [environ.core :refer [env]]
            [yet-another-craft.util :as util]
            [clojure.tools.reader :as reader]
            [clojure.core.async :refer [go]]))

(defonce entities (atom {}))
(defonce users (atom {}))
(defonce minerals (atom {}))

(comment
  (do
    (reset! entities {})
    (reset! users {})
    (reset! minerals {})
    (->> (vals @entities)
         (map :dead-for-ms))
    )
  )

(def current-time (atom 0))

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
     [:a {:href "https://github.com/edvorg/yet-another-craft"}
      [:img {:style "position: fixed; top: 0; right: 0; border: 0;"
             :src "https://camo.githubusercontent.com/e7bbb0521b397edbd5fe43e7f760759336b5e05f/68747470733a2f2f73332e616d617a6f6e6177732e636f6d2f6769746875622f726962626f6e732f666f726b6d655f72696768745f677265656e5f3030373230302e706e67"
             :alt "Fork me on GitHub"
             :data-canonical-src "https://s3.amazonaws.com/github/ribbons/forkme_right_green_007200.png"}]]
     (include-js "js/app.js")]]))

(defn get-channel-state [channel]
  {:entities @entities
   :minerals (@minerals (@users channel))})

(defn get-channel-state-initial [channel]
  (merge (get-channel-state channel) {:map (util/make-map)}))

(defn add-channel [channel username]
  (swap! users #(assoc % channel username))
  (when-not (@minerals username)
    (swap! entities #(merge % (util/make-initial-units username)))
    (swap! minerals #(assoc % username 100))))

(defn remove-channel [channel]
  (swap! users #(dissoc % channel))
  (println "socket closed"))

(defn harvest [username value]
  (println "harvest")
  (swap! minerals (fn [m] (update-in m [username] (partial + value)))))

(defn repair [username entity]
  (println "repair")
  (let [{:keys [hp max-hp]} (@entities entity)
        resources (@minerals username)]
    (if (and (< hp max-hp) (> resources 0))
      (do
        (swap! minerals (fn [m] (update-in m [username] dec)))
        (swap! entities (fn [e] (update-in e [entity :hp] inc)))))))

(defn marine [username entity]
  (println "marine")
  (let [pos (get-in @entities [entity :position])
        resources(get-in @minerals [username])
        user-entities (->> @entities
                           (map second)
                           (filter #(= (:user %) username)))]
    (when (and pos resources (>= resources util/marine-cost) (< (count user-entities) 10))
      (swap! entities #(assoc % (util/gen-id) (util/make-marine username pos)))
      (swap! minerals #(update-in % [username] (fn [cur] (- cur util/marine-cost)))))))

(defn attack [username entity target]
  (println "attack")
  (swap! entities #(update-in % [entity :target] (fn [old] {:id target}))))

(defn move [username entity x y]
  (println "move")
  (swap! entities #(update-in % [entity :target] (fn [old] {:x x :y y}))))

(defn handle-commands [username {:keys [command entity x y target] :as msg}]
  (let [{:keys [user hp]} (get-in @entities [entity])]
    (when (and hp user (> hp 0) (= user username))
      (cond
        (= command :harvest) (harvest username util/harvest-power)
        (= command :repair) (repair username entity)
        (= command :marine) (marine username entity)
        (= command :attack) (attack username entity target)
        (= command :move) (move username entity x y)))))

(defn web-socket-handler [req]
  (with-channel req channel
    (let [username (get-in req [:params :name])]
      (println "socket opened for user ")
      (println username)
      (add-channel channel username)
      (send! channel (pr-str (get-channel-state-initial channel)))
      (on-close channel (fn [status] (remove-channel channel)))
      (on-receive channel (fn [msg]
                            (handle-commands (@users channel) (read-string msg))
                            (send! channel (pr-str (get-channel-state channel))))))))

(defroutes routes
  (GET "/:name" [] home-page)
  (GET "/ws/:name" [] web-socket-handler)
  (resources "/")
  (not-found "Not Found"))

(def app
  (let [handler (wrap-defaults #'routes site-defaults)]
    (if (env :dev) (-> handler wrap-exceptions wrap-reload) handler)))

(defn core-loop-handler [time]
  (when-let [prev-time @current-time]
    (let [delta (- time prev-time)]
      (util/interpolate-entities delta entities)
      (util/process-ai delta entities))
    (let [dead-entities (->> @entities
                             (filter #(> (or (:dead-for-ms (second %)) 0) 10000))
                             (map first))]
      (doseq [id dead-entities]
        (swap! entities (fn [es] (dissoc es id))))))
  (reset! current-time time))

(defonce core-loop
  (go
    (loop []
      (core-loop-handler (System/currentTimeMillis))
      (recur))))
