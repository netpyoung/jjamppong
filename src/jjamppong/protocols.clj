(ns jjamppong.protocols
  (:refer-clojure :exclude [load]))

(defprotocol IWatcher
  (run [this])
  (stop [this]))

(defprotocol IMainWindow
  (init [this])
  (get-table [this])
  (load [this fpath]))
