(ns jjamppong.macros)

(defmacro fx-action [node action]
  `(.setOnAction ~node
                 (reify javafx.event.EventHandler
                   (handle [this# event#]
                     ~action))))
