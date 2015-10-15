(ns reagent-example.util)

(def harvest-power 1)
(def tile-size 64)
(def marine-velocity 0.1)
(def marine-cost 20)
(def marine-range 128)
(def marine-auto-range 256)

(defn distort-point
  ([{:keys [x y]} {offset-x :x offset-y :y}]
   {:x (+ offset-x 48 (- x (rand 96)))
    :y (+ offset-y 48 (- y (rand 96)))})
  ([pos]
   (distort-point pos {:x 0 :y 0})))

(defn make-marine [user {:keys [x y] :as pos}]
  {:hp 15
   :max-hp 15
   :position {:x x :y y}
   :angle (rand 360)
   :size {:x 64 :y 64}
   :type :marine
   :commands #{}
   :target (distort-point pos {:x -64 :y -64})
   :user user})

(defn make-command-centre [user {:keys [x y]}]
  {:hp 250
   :max-hp 300
   :position  {:x x :y y}
   :angle 0
   :size {:x 120 :y 120}
   :type :command-centre
   :commands #{:harvest :marine :repair}
   :target {}
   :user user})

(defn make-map []
  {:name "blood-bath"
   :width 2048
   :height 2048})

(defn select-centre-pos [{:keys [width height]}]
  {:x (+ 200 (rand (- width 400)))
   :y (+ 200 (rand (- height 400)))})

(defn destruct-vector [{:keys [x y] :as from} {to-x :x, to-y :y, :as to}]
  (let [dx (- to-x x)
        dy (- to-y y)
        distance (Math/sqrt (+ (* dx dx) (* dy dy)))
        nx (/ dx distance)
        ny (/ dy distance)]
    {:dx dx :dy dy
     :nx nx :ny ny
     :distance distance}))

(defn interpolate [{:keys [x y] :as pos} {tx :x ty :y} delta]
  (let [{dx :x dy :y} {:x (- tx x) :y (- ty y)}
        len (Math/sqrt (+ (* dx dx) (* dy dy)))
        {nx :x ny :y} {:x (/ dx len) :y (/ dy len)}
        sign (if (> ny 0.0) 180.0 -180.0)]
    (if (> len 10.0)
      {:position {:x (+ x (* nx delta)) :y (+ y (* ny delta))}
       :angle (+ 360.0 90.0 (/ (* sign (Math/acos nx)) 3.1415926))}
      nil)))

(defn interpolate-entities [time entities]
  (doseq [[id {{tx :x ty :y :as target} :target position :position}] @entities]
    (if (and tx ty)
      (if-let [pos (interpolate position target (* time marine-velocity))]
        (swap! entities (fn [es] (update-in es [id] #(merge % pos))))
        (swap! entities #(update-in % [id :target] empty))))))

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

(defn make-initial-units [username]
  (->> (let [pos (select-centre-pos {:width 2048 :height 2048})]
         [(make-command-centre username pos)
          (make-marine username pos)
          (make-marine username pos)
          (make-marine username pos)])
       (map #(vector (gen-id) %))
       (into (hash-map))))

(defn socket-url [host user]
  (str "ws://" host ":3000/ws/" user))

(defn process-ai [delta entities]
  (doseq [[entity-id {{target-id :id, move-x :x, move-y :y} :target
                      {:keys [x y] :as position} :position
                      user :user
                      hp :hp
                      type :type}] @entities]
    (when (= type :marine)
      (let [{target-pos :position target-hp :hp :as target} (@entities target-id)]
        (if target
          (if (or (= target-hp 0) (= 0 hp))
            (swap! entities #(update-in % [entity-id :target] empty))
            (let [{:keys [distance nx ny] } (destruct-vector position target-pos)]
              (if (< distance marine-range)
                (swap! entities #(update-in % [target-id :hp] (fn [cur-hp]
                                                                (-> cur-hp
                                                                    (- (-> delta
                                                                           (/ 1000)
                                                                           (* 5)))
                                                                    (max 0)))))
                (swap! entities #(update-in % [entity-id :target]
                                            (fn [old]
                                              (merge old {:x (-> distance
                                                                 (- marine-range)
                                                                 (+ 10)
                                                                 (* nx)
                                                                 (+ x))
                                                          :y (-> distance
                                                                 (- marine-range)
                                                                 (+ 10)
                                                                 (* ny)
                                                                 (+ y))})))))))
          (when-not (or move-x move-y)
              (when-let [targets (->> @entities
                                      (filter #(> marine-auto-range
                                                  (-> %
                                                      second
                                                      :position
                                                      (destruct-vector position)
                                                      :distance)))
                                      (filter #(< 0 (-> % second :hp)))
                                      (filter #(not= user (-> % second :user))))]
                (when-let [target (first targets)]
                  (swap! entities
                         #(update-in % [entity-id :target]
                                     assoc :id (first target)))))))))))
