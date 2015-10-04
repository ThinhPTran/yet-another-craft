(ns reagent-example.core
  (:require [reagent.core :as r]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [reagent-example.util :as util]
            [goog.history.EventType :as EventType]
            [chord.client :as chord])
  (:import goog.History))

(def marine-cost 10)
(def marine-velocity 0.07)
(def tile-size 64)
(def harvest-power 1)

(defonce state (r/atom (util/make-state)))
(defonce state-minerals (r/cursor state [:resources :minerals]))
(defonce state-entities (r/cursor state [:entities]))
(defonce state-selected (r/cursor state [:selected]))
(defonce state-user (r/cursor state [:user]))
(defonce state-map (r/cursor state [:map]))

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

(defn build-marine [parent]
  (if (>= @state-minerals marine-cost)
    (let [{:keys [user position]} (@state-entities parent)
          new-pos (util/select-spawn-point position {:x -64 :y -64})
          new-angle (rand 360)]
      (swap! state-minerals #(- % marine-cost))
      (add-entity (util/gen-id) (util/make-marine user new-pos new-angle)))))

(defn build-command-centre [user]
  (let [id (util/gen-id)
        pos (util/select-centre-pos @state-map)
        data (util/make-command-centre user pos)]
    (add-entity id data)
    (if (= user @state-user)
      (look-at pos))
    (build-marine id)
    (build-marine id)
    (build-marine id)
    [id data]))

(defn attack [id]
  (swap! (r/cursor state-entities [id :hp]) #(-> % (- 1) (max 0))))

(defn move-to [pos]
  (doseq [e @state-selected]
    (reset! (r/cursor state [:entities e :target]) (util/select-spawn-point pos))))

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
    (= command :harvest) (harvest harvest-power)
    (= command :marine) (build-marine entity)
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

(defn entity [[id data]]
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
                         (not= user @state-user) (attack id)
                         selected (deselect id)
                         :else (select id))}]]))

(defn entities []
  [:div (for [[id data] @state-entities]
          ^{:key id} [entity [id data]])])

(defn game-map []
  (let [{:keys [name width height]} @state-map]
    [:div {:class #{name}
           :style {:width width :height height}
           :on-click (fn [event]
                       (move-to {:x (- (.-pageX event) 31)
                                 :y (- (.-pageY event) 32)}))}]))

(defn game-page []
  [:div.game-page
   [game-map]
   [entities]])

(defn mount-root []
  (r/render [game-page] (.getElementById js/document "app")))

(defn core-loop-handler [time]
  (doseq [[id {:keys [target position]}] @state-entities]
    (if target
      (let [{:keys [position angle]} (util/interpolate position target (* time marine-velocity))]
        (if (and position angle)
          (do
            (reset! (r/cursor state-entities [id :position]) position)
            (reset! (r/cursor state-entities [id :angle]) angle)))))))

(def current-time (r/atom nil))

(defn core-loop [time]
  (let [prev-time @current-time]
    (if prev-time
      (core-loop-handler (- time prev-time))))
  (reset! current-time time)
  (js/requestAnimationFrame core-loop))

(defn init! []
  (println "started")
  (mount-root)
  (reset! state-user "ed")
  (build-command-centre "ed")
  (build-command-centre "ivan")
  (js/requestAnimationFrame core-loop))
