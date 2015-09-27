(ns reagent-example.util)

(defn make-marine [user x y angle]
  {:hp 15
   :max-hp 15
   :position {:x x :y y}
   :angle angle
   :size {:x 64 :y 64}
   :type :marine
   :commands #{}
   :target nil
   :user user
   :selected false
   :status :static})

(defn make-command-centre [user x y]
  {:hp 40
   :max-hp 40
   :position  {:x x :y y}
   :angle 0
   :size {:x 120 :y 120}
   :type :command-centre
   :commands #{:harvest :marine}
   :target nil
   :user user
   :selected false
   :status :static})

(defn make-map []
  {:name "blood-bath"
   :width 2048
   :height 2048})

(defn gen-id []
  (rand 1000000000))

(defn gen-frames [name animation time steps count x1 y1 x2 y2 width height]
  (for [i (range count)]
    (let [offset-x (* width i)
          offset-y (* height i)
          full-name (str name "-" animation "-" i)]
      (str "." full-name " { "
           "animation: " full-name " " time "s infinite steps(" steps "); "
           "} "
           "@keyframes " full-name " { "
           "0% { "
           "background-position: " (+ x1 offset-x) "px " (+ y1 offset-y) "px; "
           "} "
           "100% { "
           "background-position: " (+ x2 offset-x) "px " (+ y2 offset-y) "px; "
           "} "
           "}"))))

#_(gen-frames "marine" "run" 0.5 9 18 0 -256 0 -832 -64 0)
#_(gen-frames "marine" "shoot" 0.15 2 18 0 -128 0 -256 -64 0)
