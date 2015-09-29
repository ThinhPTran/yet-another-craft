(ns reagent-example.core
  (:require [reagent.core :as reagent :refer [atom cursor]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [reagent-example.util :as util :refer [make-marine
                                                   make-command-centre
                                                   gen-id
                                                   make-map
                                                   select-spawn-point
                                                   marine-style
                                                   command-centre-style]]
            [goog.history.EventType :as EventType])
  (:import goog.History))

(def marine-cost 10)
(def tile-size 64)

(def state (atom {:resources {:minerals 100}
                  :entities {}
                  :selected #{}
                  :user nil
                  :map (make-map)}))
(def state-minerals (cursor state [:resources :minerals]))
(def state-entities (cursor state [:entities]))
(def state-selected (cursor state [:selected]))
(def state-user (cursor state [:user]))
(def state-map (cursor state [:map]))

;; commands

(defn add-entity [id e]
  (swap! state-entities #(assoc % id e)))

(defn deselect [entity]
  (swap! state-selected #(disj % entity)))

(defn select [entity]
  (reset! state-selected #{})
  (swap! state-selected #(conj % entity)))

(defn look-at
  ([x y]
   (.scrollTo js/window (- x 100) (- y 100)))
  ([entity]
   (let [{:keys [x y]} (get-in @state-entities [entity :position])]
     (look-at x y))))

(defn build-marine [parent]
  (if (>= @state-minerals marine-cost)
    (let [{:keys [user]
           {:keys [x y]} :position} (@state-entities parent)
          {new-x :x
           new-y :y} (select-spawn-point x y)
          new-angle (rand 360)]
      (swap! state-minerals #(- % marine-cost))
      (add-entity (gen-id) (make-marine user
                                        (- new-x 64)
                                        (- new-y 64)
                                        new-angle)))))

(defn build-command-centre [user]
  (let [{:keys [width height]} @state-map
        id (gen-id)
        x (+ 100 (rand (- width 200)))
        y (+ 100 (rand (- height 200)))
        data (make-command-centre user x y)]
    (add-entity id data)
    (if (= user @state-user)
      (look-at x y))
    (build-marine id)
    (build-marine id)
    (build-marine id)
    [id data]))

(defn attack [id]
  (swap! (cursor state-entities [id :hp]) #(-> % (- 1) (max 0))))

(defn move-to [x y]
  (doseq [e @state-selected]
    (reset! (cursor state [:entities e :position]) (select-spawn-point x y))))

(defn harvest []
  (swap! state-minerals inc))

(defn repair [entity]
  (let [{:keys [hp max-hp]} (@state-entities entity)
        minerals @state-minerals]
    (if (and (< hp max-hp) (> minerals 0))
      (do
        (swap! state-minerals dec)
        (swap! (cursor state-entities [entity :hp]) inc)))))

(defn execute-command [entity command]
  (cond
    (= command :harvest) (harvest)
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

(defn compute-angle-id [angle]
  (.round js/Math (* (/ (mod angle 360.0) 360.0) 18)))

(defn state-styles [data]
  (let [{:keys [hp type angle]} data
        angle-id (compute-angle-id angle)]
    (cond
      (= type :marine) (marine-style hp angle-id)
      (= type :command-centre) (command-centre-style hp))))

(defn commands-list [entity commands selected]
  [:div.commands {:style {:display (if selected "initial" "none")}}
   (for [command commands]
     ^{:key command } [:div {:class (str "command-" (name command))
                             :on-click #(execute-command entity command)}])])

(defn entity [[id data]]
  (let [width (-> data :size :x)
        height (-> data :size :y)
        x (-> data :position :x)
        y (-> data :position :y)
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
     [commands-list id commands selected]
     [hp-bar hp-width]
     [selection selected width height]
     [:div {:class (state-styles data)
            :on-click #(cond
                         (not= user @state-user) (attack id)
                         selected (deselect id)
                         :else (select id))}]]))

(defn entities []
  [:div (for [[id data] @state-entities]
          ^{:key id} [entity [id data]])])

(defn resources []
  [:div.resources
   [:div "minerals : " @state-minerals]])

(defn game-map []
  (let [{:keys [name width height]} @state-map]
    [:div {:class #{name}
           :style {:width width :height height}}
     (for [i (range (/ width tile-size))
           j (range (/ height tile-size))]
       (let [x (* i tile-size)
             y (* j tile-size)]
         ^{:key [i j]} [:div.tile {:style {:top y
                                           :left x
                                           :width tile-size
                                           :height tile-size}
                                   :on-click #(move-to x y)}]))]))

(defn game-page []
  [:div.game-page
   [game-map]
   [entities]
   [resources]])

(defn current-page []
  [:div
   [(session/get :current-page)]])


(defn mount-root []
  (reagent/render [game-page] (.getElementById js/document "app"))
  (reset! state-user "ed")
  (build-command-centre "ed")
  (build-command-centre "ivan"))

(defn init! []
  (println "started")
  (mount-root))
