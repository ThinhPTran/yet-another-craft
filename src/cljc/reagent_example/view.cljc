(ns reagent-example.view
  (:require [reagent-example.util :as util]))

(defn hp-bar [hp-width]
  [:div.hp-bar {:style {:width hp-width}}])

(defn selection [selected width height]
  [:div.selection {:style {:display (if selected "initial" "none")
                           :width width
                           :height height}}])

(defn commands-list [entity commands selected execute-command]
  [:div.commands {:style {:display (if selected "initial" "none")}}
   (for [command commands]
     ^{:key command } [:div {:class (str "command-" (name command))
                             :on-click #(execute-command entity command)}])])

(defn resources [selected type
                 state-minerals]
  (if (and selected (= type :command-centre))
    [:div.resources "minerals : " @state-minerals]))

(defn username [user width height]
  [:div.username {:style {:width width :height height}} user])

(defn entity [id data current-user
              attack select execute-command
              state-selected state-minerals]
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
     [commands-list id commands selected execute-command]
     [resources selected type state-minerals]
     [:div {:class (util/state-styles hp type angle)
            :on-click #(if (= user current-user) (select id) (attack id))}]]))

(defn entities [attack select execute-command
                state-entities state-user state-selected state-minerals]
  (let [entities @state-entities
        current-user @state-user]
    [:div (for [[id data] entities]
            ^{:key id} [entity
                        id data current-user
                        attack select execute-command
                        state-selected state-minerals])]))

(defn game-map [move state-map]
  (let [{:keys [name width height]} @state-map]
    [:div {:class #{name}
           :style {:width width :height height}
           :on-click #(move %)}]))

(defn game-page [move attack select execute-command
                 state-map state-entities state-minerals state-selected state-user]
  [:div.game-page
   [game-map move state-map]
   [entities
    attack select execute-command
    state-entities state-user state-selected state-minerals]])
