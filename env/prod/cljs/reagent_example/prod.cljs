(ns yet-another-craft.prod
  (:require [yet-another-craft.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
