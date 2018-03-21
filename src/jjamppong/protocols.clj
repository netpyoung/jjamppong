(ns jjamppong.protocols
  (:refer-clojure :exclude [load]))

;;TODO(kep): need to manage this
(defonce +ONCE+ (javafx.embed.swing.JFXPanel.))

(defprotocol IWatcher
  (run [this])
  (stop [this]))

(defprotocol IWindowMain
  (init [this])
  (get-table [this])
  (update-predicate [this filter-list])
  (load [this fpath])
  (update-status-message [this message])
  )
