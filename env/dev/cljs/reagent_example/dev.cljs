(ns ^:figwheel-no-load yet-another-craft.dev
  (:require [yet-another-craft.core :as core]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(figwheel/watch-and-reload
 :nrepl-port 7002
 :websocket-url "ws://localhost:3000/figwheel-ws"
 :jsload-callback core/mount-root)

(core/init!)
