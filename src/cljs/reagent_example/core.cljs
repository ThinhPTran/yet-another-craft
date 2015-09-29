(ns reagent-example.core
  (:require [reagent.core :as reagent :refer [atom cursor]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [reagent-example.util :as util :refer [make-marine
                                                   make-command-centre
                                                   gen-id
                                                   make-map
                                                   select-spawn-point]]
            [goog.history.EventType :as EventType])
  (:import goog.History))

(def ^:const marine-cost 10)

(def app-state (atom {:resources {:minerals 100}
                      :entities {}
                      :selected #{}
                      :user "ed"
                      :map (make-map)}))
(def app-state-minerals (cursor app-state [:resources :minerals]))
(def app-state-entities (cursor app-state [:entities]))
(def app-state-selected (cursor app-state [:selected]))
(def app-state-user (cursor app-state [:user]))
(def app-state-map (cursor app-state [:map]))
(def tile-size 64)

;; utils

(defn swap-in! [where path f]
  (swap! where #(update-in % path f)))

(defn reset-in! [where path v]
  (swap-in! where path (fn [x] v)))

;; commands

(defn add-entity [id e]
  (swap! app-state-entities #(assoc % id e)))

(defn deselect [entity]
  (swap! app-state-selected #(disj % entity)))

(defn select [entity]
  ;; (doseq [e @app-state-selected]
  ;;   (deselect e))
  (swap! app-state-selected #(conj % entity)))

(defn look-at
  ([x y]
   (.scrollTo js/window (- x 100) (- y 100)))
  ([entity]
   (let [{:keys [x y]} (get-in @app-state-entities [entity :position])]
     (look-at x y))))

(defn build-marine [parent]
  (if (>= @app-state-minerals marine-cost)
    (let [{:keys [user]
           {:keys [x y]} :position} (@app-state-entities parent)
          {new-x :x
           new-y :y} (select-spawn-point x y)
          new-angle (rand 360)]
      (swap-in! app-state [:resources :minerals] #(- % marine-cost))
      (add-entity (gen-id) (make-marine user new-x new-y new-angle)))))

(defn build-command-centre [user]
  (let [{:keys [width height]} @app-state-map
        id (gen-id)
        x (+ 100 (rand (- width 200)))
        y (+ 100 (rand (- height 200)))
        data (make-command-centre user x y)]
    (add-entity id data)
    (if (= user @app-state-user)
      (look-at x y))
    (build-marine id)
    (build-marine id)
    (build-marine id)
    [id data]))

(defn attack [id]
  (swap-in! app-state [:entities id :hp] #(max 0 (- % 1))))

(defn move-to [x y]
  (doseq [e @app-state-selected]
    (reset-in! app-state [:entities e :position] (select-spawn-point x y))))

(defn harvest []
  (swap-in! app-state [:resources :minerals] inc))

(defn execute-command [entity command]
  (cond
    (= command :harvest) (harvest)
    (= command :marine) (build-marine entity)))

;; example

(def ed-centre (first (build-command-centre "ed")))
(def ivan-centre (first (build-command-centre "ivan")))

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
        type-class (name type)
        angle-id (compute-angle-id angle)]
    (cond
      (= type :marine) (cond
                         (<= hp 0) (str type-class " marine-die")
                         :else (str type-class " marine-run-" angle-id))
      (= type :command-centre) #{type-class}
      :else #{type-class})))

(defn commands-list [entity commands selected]
  [:div.commands {:style {:display (if selected "initial" "none")}}
   (for [command commands]
     [:div {:class (str "command-"(name command))
            :on-click #(execute-command entity command)}])])

(defn entity [[id data]]
  (let [width (-> data :size :x)
        height (-> data :size :y)
        x (-> data :position :x)
        y (-> data :position :y)
        hp (data :hp)
        max-hp (data :max-hp)
        hp-width (* (/ hp max-hp) width)
        selected (@app-state-selected id)
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
                         (not= user @app-state-user) (attack id)
                         selected (deselect id)
                         :else (select id))}]]))

(defn entities []
  [:div (for [data @app-state-entities]
          [entity data])])

(defn resources []
  [:div.resources
   [:div "minerals : " @app-state-minerals]])

(defn game-map []
  (let [{:keys [name width height]} @app-state-map]
    [:div {:class #{name}
           :style {:width width
                   :height height}}
     (for [i (range (/ width tile-size))
           j (range (/ height tile-size))]
       (let [x (* i tile-size)
             y (* j tile-size)]
         [:div.tile {:style {:top y
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

(reagent/render [game-page] (.getElementById js/document "app"))
