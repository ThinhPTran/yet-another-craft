(ns yet-another-craft.client)

(defn look-at
  ([{:keys [x y]}]
   (.scrollTo js/window (- x 128) (- y 128))))

(defn setup-camera [username entities]
  (when-let [look-pos (->> entities
                           (filter #(= username (-> % second :user)))
                           first
                           second
                           :position)]
    (look-at look-pos)))
