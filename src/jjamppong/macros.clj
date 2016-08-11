(ns jjamppong.macros
  (:import
   [javafx.fxml FXMLLoader FXML]))

(defmacro fx-action [node action]
  `(.setOnAction ~node
                 (reify javafx.event.EventHandler
                   (handle [this# event#]
                     ~action))))
