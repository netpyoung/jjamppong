(ns jjamppong.protocols
  (:refer-clojure :exclude [load]))

(defprotocol IWatcher
  (run [this])
  (stop [this]))

(defprotocol IMainWindow
  (init [this])
  (get-table [this])
  (update-predicate [this filter-list])
  (load [this fpath]))

(definterface IMainWindowFX
  (close [])
  (^{:tag void} on_btn_start [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_clear [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_stop [^javafx.event.ActionEvent event])
  (^{:tag void} on_check_lvl [^javafx.event.ActionEvent event])
  (^{:tag void} on_txt_filter_changed [^javafx.scene.input.KeyEvent event])
  (^{:tag void} on_check_ignorecase [^javafx.event.ActionEvent event])
  (^{:tag void} on_check_regex [^javafx.event.ActionEvent event])
  )
