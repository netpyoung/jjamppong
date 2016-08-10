(ns jjamppong.protocols)

(defprotocol IWatcher
  (run [this])
  (stop [this]))

(defprotocol IMainWindow
  (init [this]))
