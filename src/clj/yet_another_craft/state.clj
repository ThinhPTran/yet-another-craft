(ns yet-another-craft.state
  (:require [mount.core :refer [defstate]]))

(defstate entities :start (atom {}))
(defstate users :start (atom {}))
(defstate minerals :start (atom {}))
