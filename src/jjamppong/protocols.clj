(ns jjamppong.protocols
  (:refer-clojure :exclude [load]))

(defprotocol IWatcher
  (run [this])
  (stop [this]))

(defprotocol IMainWindow
  (init [this])
  (load [this fpath]))
