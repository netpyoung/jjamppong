(ns jjamppong.interfaces)

(definterface IWindowMainFX
  (close [])
  (^{:tag void} on_btn_scan [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_start [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_clear [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_stop [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_filter [^javafx.event.ActionEvent event])

  (^{:tag void} on_check_lvl [^javafx.event.ActionEvent event])
  (^{:tag void} on_txt_filter_changed [^javafx.scene.input.KeyEvent event])
  (^{:tag void} on_check_ignorecase [^javafx.event.ActionEvent event])
  (^{:tag void} on_check_regex [^javafx.event.ActionEvent event])
  )

(definterface IItmHighlightFX
  (update1 [item])
  (^{:tag void} on_check [^javafx.event.ActionEvent event])
  )

(definterface IWindowHighlightFX
  (hello [])
  (^{:tag void} on_btn_new [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_remove [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_up [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_down [^javafx.event.ActionEvent event])
  )
