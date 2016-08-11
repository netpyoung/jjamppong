(ns jjamppong.systems
  (:require
   [system.core :refer [defsystem]]
   [jjamppong.window :refer [dev-new-window prod-new-window]]))

(defsystem dev-system
  [:window (dev-new-window)])

(defsystem main-system
  [:window (prod-new-window)])
