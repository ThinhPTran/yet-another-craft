(ns yet-another-craft.core
  (:require [reagent.core :as r]
            [taoensso.timbre :as timbre]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [yet-another-craft.util :as util]
            [yet-another-craft.client :as client]
            [yet-another-craft.view :as view]
            [taoensso.timbre :as timbre]
            [chord.client :as chord]
            [cljs.core.async :refer [<! >!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce state-minerals (r/atom 0))
(defonce state-entities (r/atom {}))
(defonce state-selected (r/atom #{}))
(defonce state-user (r/atom ""))
(defonce state-map (r/atom nil))
(defonce state-channel (r/atom nil))

(def current-time (r/atom 0))

;; Commands

(defn entity-type [entity]
  (get-in @state-entities [entity :type]))

(defn select [entity]
  (let [{:keys [hp type]} (@state-entities entity)
        not-same-type (some #(not= type (entity-type %)) @state-selected)
        selected-count (count @state-selected)
        select-all (and (= selected-count 1) (= (first @state-selected) entity))
        deselect-all (or (contains? @state-selected entity) not-same-type)]
    (if select-all
      (reset! state-selected (->> @state-entities
                                  (filter #(= @state-user (-> % second :user)))
                                  (filter #(= type (-> % second :type)))
                                  (filter #(> (-> % second :hp) 0))
                                  (map first)
                                  set))
      (when deselect-all
        (reset! state-selected #{})))
    (when (> hp 0)
      (swap! state-selected #(conj % entity)))))

(defn execute-command [entity command & {:as params}]
  (go
    (>! @state-channel (merge {:command command :entity entity} params))
    (<! @state-channel)))

(defn attack [target]
  (doseq [e @state-selected]
    (execute-command e :attack, :target target)))

(defn move [event]
  (doseq [e @state-selected]
    (let [rpos (util/distort-point {:x (.-pageX event) :y (.-pageY event)})]
      (execute-command e :move, :x (rpos :x), :y (rpos :y)))))

(defn mount-root []
  (r/render [view/game-page
             move attack select execute-command
             state-map state-entities state-minerals state-selected state-user]
            (.getElementById js/document "app")))

;; Game cycle

(defn core-loop-handler [time]
  (when-let [prev-time @current-time]
    (util/interpolate-entities (- time prev-time) state-entities))
  (reset! current-time time))

(defn network-loop-handler [channel]
  (go
    (>! channel {:command :ping})
    (let [message (-> channel <! :message)
          minerals (:minerals message)
          entities (:entities message)]
      (when minerals
        (reset! state-minerals minerals))
      (when entities
        (reset! state-entities entities)))))

(defn core-loop [channel]
  (js/requestAnimationFrame
   (fn [time]
     (core-loop-handler time)
     (network-loop-handler channel)
     (core-loop channel))))

(defn login [username]
  (go
    (let [ws-url (util/socket-url (-> js/window .-location .-hostname) username)
          {:keys [ws-channel]} (<! (chord/ws-ch ws-url))
          {:keys [message error]} (<! ws-channel)]
      (if-not error
        (do (reset! state-channel ws-channel)
            (reset! state-entities (:entities message))
            (reset! state-map (:map message))
            (reset! state-minerals (:minerals message))
            (reset! state-user username)
            (mount-root)
            (client/setup-camera username @state-entities)
            (core-loop ws-channel))
        (timbre/debug error)))))

(secretary/set-config! :prefix "#")

(secretary/defroute "/:name" {:keys [name] :as params}
  (login name))

(defn init! []
  (timbre/debug "started")
  (secretary/dispatch! (-> js/window .-location .-pathname)))
