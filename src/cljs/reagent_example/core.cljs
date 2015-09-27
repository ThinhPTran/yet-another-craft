(ns reagent-example.core
    (:require [reagent.core :as reagent :refer [atom cursor]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [reagent-example.util :as util :refer [make-marine
                                                     make-command-centre
                                                     gen-id
                                                     make-map]]
              [goog.history.EventType :as EventType])
    (:import goog.History))

(def app-state (atom {:resources {:minerals 100}
                      :entities {}
                      :selected #{}
                      :user "ed"
                      :map (make-map)}))
(def app-state-resources (cursor app-state [:resources]))
(def app-state-minerals (cursor app-state-resources [:minerals]))
(def app-state-entities (cursor app-state [:entities]))
(def app-state-selected (cursor app-state [:selected]))
(def app-state-user (cursor app-state [:user]))
(def app-state-map (cursor app-state [:map]))
(def tile-size 32)

(defn add-entity [id e]
  (swap! app-state (fn [state] (update-in state [:entities] #(assoc % id e)))))

(defn deselect [entity]
  (swap! app-state #(update-in % [:entities entity :selected] (fn [a] false)))
  (swap! app-state #(update-in % [:selected] (fn [s] (disj s entity)))))

(defn select [entity]
  (doseq [e @app-state-selected]
    (deselect e))
  (swap! app-state #(update-in % [:entities entity :selected] (fn [a] true)))
  (swap! app-state #(update-in % [:selected] (fn [s] (conj s entity)))))

(defn look-at
  ([x y]
   (.scrollTo js/window (- x 100) (- y 100)))
  ([entity]
   (let [{:keys [x y]} (get-in @app-state-entities [entity :position])]
     (look-at x y))))

(defn build-command-centre [user]
  (let [{:keys [width height]} @app-state-map
        id (gen-id)
        x (+ 100 (rand (- width 200)))
        y (+ 100 (rand (- height 200)))
        data (make-command-centre user x y)]
    (add-entity id data)
    (if (= user @app-state-user)
      (look-at x y))
    [id data]))

(defn build-marine [parent]
  (let [{:keys [user]
         {:keys [x y]} :position} (@app-state-entities parent)
        new-x (- x (rand 100))
        new-y (- y (rand 100))
        new-angle (rand 360)]
    (add-entity (gen-id) (make-marine user new-x new-y new-angle))))

(defn attack [id]
  (swap! app-state #(update-in % [:entities id :hp] (fn [hp] (max 0 (- hp 1))))))

(defn move-to [x y]
  (doseq [e @app-state-selected]
    (swap! app-state #(update-in % [:entities e :position] (fn [pos] {:x x :y y})))))

(defn harvest []
  nil)

(defn execute-command [command]
  (js/alert (name command)))

;; example

(def ed-centre (first (build-command-centre "ed")))
(def ivan-centre (first (build-command-centre "ivan")))
(build-marine ed-centre)
(build-marine ed-centre)
(build-marine ed-centre)
(build-marine ivan-centre)
(build-marine ivan-centre)
(build-marine ivan-centre)


;; -------------------------
;; Views

(defn hp-bar [hp-width]
  [:div.hp-bar {:style {:width hp-width}}])

(defn selection [selected width height]
  [:div.selection {:style {:display (if selected "initial" "none")
                           :width width
                           :height height}}])

(defn state-styles [data]
  (let [{:keys [hp type angle]} data
        type-class (name type)
        angle-id (.round js/Math (* (/ (mod angle 360.0) 360.0) 18))]
    (cond
      (= type :marine) (cond
                         (<= hp 0) (str type-class " marine-die")
                         :else (str type-class " marine-run-" angle-id))
      (= type :command-centre) #{type-class}
      :else #{type-class})))

(defn commands-list [commands selected]
  [:div.commands {:style {:display (if selected "initial" "none")}}
   (for [command commands]
     [:div {:class (str "command-"(name command))
            :on-click #(execute-command command)}])])

(defn entity [[id data]]
  (let [width (-> data :size :x)
        height (-> data :size :y)
        x (-> data :position :x)
        y (-> data :position :y)
        hp (data :hp)
        max-hp (data :max-hp)
        hp-width (* (/ hp max-hp) width)
        selected (data :selected)
        type (data :type)
        user (data :user)
        commands (data :commands)]
    [:div.entity {:style {:width width
                          :height height
                          :left x
                          :top y}
                  :on-click #(cond
                               (not= user @app-state-user) (attack id)
                               selected (deselect id)
                               :else (select id))}
     [commands-list commands selected]
     [hp-bar hp-width]
     [selection selected width height]
     [:div {:class (state-styles data)}]]))

(defn entities []
  [:div (for [data @app-state-entities]
          [entity data])])

(defn resources [data]
  [:div.resources
   [:div [:a {:href "#/"} "leave game"]]
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

(defn home-page []
  [:div.home-page
   [:h2 "Welcome our awesome game!"]
   [:div [:a {:href "#/game"} "start"]]])

(defn game-page []
  [:div.game-page
   [game-map]
   [entities]
   [resources @app-state-resources]])

(defn current-page []
  [:div
   [(session/get :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/game" []
  (session/put! :current-page #'game-page))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app"))
  (look-at ed-centre))

(defn init! []
  (hook-browser-navigation!)
  (mount-root))
