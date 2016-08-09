(ns jjamppong.core
  (:gen-class)
  (:require
   [system.repl :refer [system set-init! start stop reset]]
   [jjamppong.systems :refer [main-system]]))


(defn -main
  [& args]
  (set-init! #'main-system)
  (start))
