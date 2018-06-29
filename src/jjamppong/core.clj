(ns jjamppong.core
  (:gen-class)
  (:require
   [system.repl :refer [set-init! start reset]]
   [jjamppong.systems :refer [main-system]]))

(defn -main
  [& args]
  (set-init! #'main-system)
  (start)
  @(promise))
