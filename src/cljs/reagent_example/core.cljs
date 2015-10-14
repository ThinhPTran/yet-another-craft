(ns reagent-example.core
  (:require [reagent.core :as r]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [reagent-example.util :as util]
            [chord.client :as chord]
            [cljs.core.async :refer [<! >! put! take! close!]]
            [secretary.core :as secretary :refer-macros [defroute]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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

(defn select [entity]
  (if (or (contains? @state-selected entity)
          (some #(not= (get-in @state-entities [entity :type])
                       (get-in @state-entities [% :type]))
                @state-selected))
    (reset! state-selected #{}))
  (swap! state-selected #(conj % entity)))

(defn execute-command [entity command & {:as params}]
  (go
    (>! @state-channel (merge {:command command :entity entity} params))
    (<! @state-channel)))

(defn attack [target]
  (doseq [e @state-selected]
    (execute-command e :attack, :target target)))

(defn move [pos]
  (doseq [e @state-selected]
    (let [rpos (util/select-spawn-target pos {:x 0 :y 0})]
      (execute-command e :move, :x (rpos :x), :y (rpos :y)))))

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

(defn username [user width height]
  [:div.username {:style {:width width :height height}} user])

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
    [:div.entity {:style {:width width, :height height, :left x, :top y}}
     [username user width height]
     [hp-bar hp-width]
     [selection selected width height]
     [commands-list id commands selected]
     [resources selected type]
     [:div {:class (util/state-styles hp type angle)
            :on-click #(if (= user current-user) (select id) (attack id))}]]))

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
                       (move {:x (.-pageX event) :y (.-pageY event)}))}]))

(defn game-page []
  [:div.game-page
   [game-map]
   [entities]])

(defn mount-root []
  (r/render [game-page] (.getElementById js/document "app")))

;; Game cycle

(defn core-loop-handler [time]
  (when-let [prev-time @current-time]
    (util/interpolate-entities (- time prev-time) state-entities))
  (reset! current-time time))

(defn core-loop [channel]
  (js/requestAnimationFrame
   (fn [time]
     (core-loop-handler time)
     (go
       (>! channel {:command :ping})
       (let [message (-> channel <! :message)
             minerals (:minerals message)
             entities (:entities message)]
         (when minerals
           (reset! state-minerals minerals))
         (when entities
           (reset! state-entities entities))))
     (core-loop channel))))

(defn login [username]
  (go
    (let [{:keys [ws-channel]} (<! (chord/ws-ch (str "ws://"
                                                     (-> js/window .-location .-hostname)
                                                     ":3000/ws/"
                                                     username)))
          {:keys [message error]} (<! ws-channel)]
      (if-not error
        (do (reset! state-channel ws-channel)
            (reset! state-entities (:entities message))
            (reset! state-map (:map message))
            (reset! state-minerals (:minerals message))
            (reset! state-user username)
            (mount-root)
            (core-loop ws-channel))
        (js/console.log error)))))

(secretary/set-config! :prefix "#")

(defroute "/:name" {:keys [name] :as params}
  (login name)
  (js/console.log (str params))
  (js/console.log name))

(defn init! []
  (println "started")
  (secretary/dispatch! (-> js/window .-location .-pathname)))
