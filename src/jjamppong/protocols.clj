(ns jjamppong.protocols)

(defprotocol IWatcher
  (run [this async->fn])
  (stop [this]))

(defprotocol IMainWindow
  (init [this]))
