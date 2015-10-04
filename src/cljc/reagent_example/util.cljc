(ns reagent-example.util)

(defn make-marine [user {:keys [x y]} angle]
  {:hp 15
   :max-hp 15
   :position {:x x :y y}
   :angle angle
   :size {:x 64 :y 64}
   :type :marine
   :commands #{}
   :target nil
   :user user})

(defn make-command-centre [user {:keys [x y]}]
  {:hp 30
   :max-hp 40
   :position  {:x x :y y}
   :angle 0
   :size {:x 120 :y 120}
   :type :command-centre
   :commands #{:harvest :marine :repair}
   :target nil
   :user user})

(defn select-centre-pos [{:keys [width height]}]
  {:x (+ 200 (rand (- width 400)))
   :y (+ 200 (rand (- height 400)))})

(defn select-spawn-point
  ([{:keys [x y]} {offset-x :x offset-y :y}]
   {:x (+ offset-x 32 (- x (rand 64)))
    :y (+ offset-y 32 (- y (rand 64)))})
  ([pos]
   (select-spawn-point pos {:x 0 :y 0})))

(defn interpolate [{:keys [x y] :as pos} {tx :x ty :y} delta]
  (let [{dx :x dy :y} {:x (- tx x) :y (- ty y)}
        len (Math/sqrt (+ (* dx dx) (* dy dy)))
        {nx :x ny :y} {:x (/ dx len) :y (/ dy len)}
        sign (if (> ny 0) 180 -180)]
    (if (> len 10)
      {:position {:x (+ x (* nx delta)) :y (+ y (* ny delta))}
       :angle (+ 360 90 (/ (* sign (Math/acos nx)) 3.1415926))}
      nil)))

(defn make-map []
  {:name "blood-bath"
   :width 2048
   :height 2048})

(defn make-state []
  {:resources {:minerals 100}
   :entities {}
   :selected #{}
   :user nil
   :map (make-map)})

(defn marine-style [hp angle-id]
  (cond
    (<= hp 0) "marine marine-die"
    :else (str "marine" " marine-run-" angle-id)))

(defn command-centre-style [hp]
  (cond
    (<= hp 0) "command-centre command-centre-die"
    :else (str "command-centre")))

(defn compute-angle-id [angle]
  (Math/round (* (/ (mod angle 360.0) 360.0) 18)))

(defn state-styles [hp type angle]
  (let [angle-id (compute-angle-id angle)]
    (cond
      (= type :marine) (marine-style hp angle-id)
      (= type :command-centre) (command-centre-style hp))))

(defn gen-id []
  (rand 1000000000))

;; (defn gen-frames [name animation time steps count x1 y1 x2 y2 width height]
;;   (for [i (range count)]
;;     (let [offset-x (* width i)
;;           offset-y (* height i)
;;           full-name (str name "-" animation "-" i)]
;;       (str "." full-name " { "
;;            "animation: " full-name " " time "s infinite steps(" steps "); "
;;            "} "
;;            "@keyframes " full-name " { "
;;            "0% { "
;;            "background-position: " (+ x1 offset-x) "px " (+ y1 offset-y) "px; "
;;            "} "
;;            "100% { "
;;            "background-position: " (+ x2 offset-x) "px " (+ y2 offset-y) "px; "
;;            "} "
;;            "}"))))

;; #_(gen-frames "marine" "run" 0.5 9 18 0 -256 0 -832 -64 0)
;; #_(gen-frames "marine" "shoot" 0.15 2 18 0 -128 0 -256 -64 0)
