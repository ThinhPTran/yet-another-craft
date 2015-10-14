(ns reagent-example.core
  (:require [reagent.core :as r]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [reagent-example.util :as util]
            [goog.history.EventType :as EventType]
            [chord.client :as chord]
            [cljs.core.async :refer [<! >! put! take! close!]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:import goog.History))

(defonce state-minerals (r/atom 0))
(defonce state-entities (r/atom {}))
(defonce state-selected (r/atom #{}))
(defonce state-user (r/atom ""))
(defonce state-map (r/atom nil))
(defonce state-channel (r/atom nil))

(def current-time (r/atom 0))
(def network-time (r/atom 0))

;; Utils

(defn look-at
  ([{:keys [x y]}]
   (.scrollTo js/window (- x 100) (- y 100))))

;; Commands

(defn add-entity [id e]
  (swap! state-entities #(assoc % id e)))

(defn deselect [entity]
  (swap! state-selected #(disj % entity)))

(defn select [entity]
  (reset! state-selected #{})
  (swap! state-selected #(conj % entity)))

(defn attack [id]
  (swap! (r/cursor state-entities [id :hp]) #(-> % (- 1) (max 0))))

(defn move-to [pos]
  (doseq [e @state-selected]
    (reset! (r/cursor state-entities [e :target]) pos)))

(defn harvest [value]
  (swap! state-minerals (partial + value)))

(defn repair [entity]
  (let [{:keys [hp max-hp]} (@state-entities entity)
        minerals @state-minerals]
    (if (and (< hp max-hp) (> minerals 0))
      (do
        (swap! state-minerals dec)
        (swap! (r/cursor state-entities [entity :hp]) inc)))))

(defn execute-command [entity command]
  (cond
    (= command :harvest) (harvest util/harvest-power)
    (= command :marine) nil ;; (build-marine entity)
    (= command :repair) (repair entity)))

;; -------------------------
;; Views

(defn hp-bar [hp-width]
  [:div.hp-bar {:style {:width hp-width}}])

(defn selection [selected width height]
  [:div.selection {:style {:display (if selected "initial" "none")
                           :width width
                           :height height}}])

(defn commands-list [entity commands selected]
  [:div.commands {:style {:display (if selected "initial" "none")}}
   (for [command commands]
     ^{:key command } [:div {:class (str "command-" (name command))
                             :on-click #(execute-command entity command)}])])

(defn resources [selected type]
  (if (and selected (= type :command-centre))
    [:div.resources "minerals : " @state-minerals]))

(defn entity [id data current-user]
  (let [width (-> data :size :x)
        height (-> data :size :y)
        x (-> data :position :x)
        y (-> data :position :y)
        angle (data :angle)
        hp (data :hp)
        max-hp (data :max-hp)
        hp-width (* (/ hp max-hp) width)
        selected (@state-selected id)
        type (data :type)
        user (data :user)
        commands (data :commands)]
    [:div.entity {:style {:width width
                          :height height
                          :left x
                          :top y}}
     [hp-bar hp-width]
     [selection selected width height]
     [commands-list id commands selected]
     [resources selected type]
     [:div {:class (util/state-styles hp type angle)
            :on-click #(cond
                         (not= user current-user) (attack id)
                         selected (deselect id)
                         :else (select id))}]]))

(defn entities []
  (let [entities @state-entities
        current-user @state-user]
    [:div (for [[id data] entities]
            ^{:key id} [entity id data current-user])]))

(defn game-map []
  (let [{:keys [name width height]} @state-map]
    [:div {:class #{name}
           :style {:width width :height height}
           :on-click (fn [event]
                       (move-to {:x (.-pageX event) :y (.-pageY event)}))}]))

(defn game-page []
  [:div.game-page
   [game-map]
   [entities]])

(defn mount-root []
  (r/render [game-page] (.getElementById js/document "app")))

;; Game cycle

(defn core-loop-handler [channel time]
  (util/interpolate-entities time state-entities))

(defn core-loop [channel]
  (js/requestAnimationFrame
   (fn [time]
     (when-let [prev-time @current-time]
       (core-loop-handler channel (- time prev-time)))
     (reset! current-time time)
     (go
       (>! channel time)
       (let [message (-> channel <! :message)
             minerals (:minerals message)
             entities (:entities message)]
         (when minerals
           (reset! state-minerals minerals))
         (when entities
           (reset! state-entities entities))))
     (core-loop channel))))

(defn init! []
  (println "started")
  (go
    (let [{:keys [ws-channel]} (<! (chord/ws-ch "ws://localhost:3000/ws/edwardo"))
          {:keys [message error]} (<! ws-channel)]
      (if-not error
        (do (reset! state-channel ws-channel)
            (reset! state-entities (:entities message))
            (reset! state-map (:map message))
            (reset! state-minerals (:minerals message))
            (reset! state-user "edwardo")
            (js/console.log "Initial state: " (pr-str (:entities message)))
            (mount-root)
            (core-loop ws-channel))
        (js/console.log error)))))
