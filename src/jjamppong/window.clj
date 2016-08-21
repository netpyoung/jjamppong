(ns jjamppong.window
  (:require
   [system.repl :refer [system]]
   [com.stuartsierra.component :as component]
   [jjamppong.macros :as m]
   [jjamppong.protocols :as impl]
   [jjamppong.watcher :as watcher]
   [jjamppong.window.mainwindow :as mainwindow]
   [clojure.reflect :as r]
   [clojure.string :as str]
   [named-re.re :as re]
   [clojure.java.shell]
   [clojure.core.async :as async])


  (:import
   ;; [jjamppong.protocols.IMainWindowFX]
   [java.lang Enum]
   [java.net URL]
   [java.util ResourceBundle]

   ;; javafx
   [java.awt.event.InputMethodEvent]
   [javafx.beans.property SimpleStringProperty]
   [javafx.beans.value ObservableValue]
   [javafx.fxml FXMLLoader FXML]
   [javafx.stage Stage StageBuilder Modality]
   [javafx.scene Scene]
   [javafx.collections.transformation FilteredList]

   [javafx.scene.control Button Alert Alert$AlertType Cell TableColumn TableRow TableCell TableView SelectionMode]
   [javafx.scene.control.cell PropertyValueFactory MapValueFactory]
   [javafx.scene.input KeyCode KeyCodeCombination KeyCombination KeyEvent KeyCombination$Modifier
    Clipboard ClipboardContent]
   [javafx.event ActionEvent EventHandler]
   [javafx.collections FXCollections ListChangeListener]))

(defn register-drag-drop-event [scene controller]
  ;; TODO(kep): need to write EventHandler macro.
  (doto scene
    (.setOnDragOver
     (reify javafx.event.EventHandler
       (handle [this event]
         (let [db (.getDragboard event)]
           (if (.hasFiles db)
             (doto event
               (.acceptTransferModes
                (into-array [javafx.scene.input.TransferMode/COPY]))
               (.consume)))))))

    (.setOnDragDropped
     (reify javafx.event.EventHandler
       (handle [this event]
         (let [db (.getDragboard event)]
           (if (. db hasFiles)
             (do
               (->> db
                    (.getFiles)
                    (first)
                    (.getAbsolutePath)
                    (impl/load controller))
               (. event (setDropCompleted true)))
             (. event (setDropCompleted false)))
           (. event consume)))))))


(defrecord Window [is-dev _stage _window]
  component/Lifecycle
  (start [this]
    (javafx.application.Platform/setImplicitExit false)
    (javafx.application.Platform/runLater
     #(do
        (let [fxml (clojure.java.io/resource "layout.fxml")
              controller (mainwindow/gen-MainWindow)
              loader (doto (FXMLLoader. fxml)
                       (.setController controller))
              scene (doto (Scene. (.load loader))
                      (.. getStylesheets (add "layout.css")))
              stage (doto (.build (StageBuilder/create))
                      (.setTitle "jjamppong")
                      (.setScene scene)
                      (.show)
                      (.setOnCloseRequest
                       (proxy [EventHandler] []
                         (handle [^ActionEvent event]
                           (when-not is-dev
                             (System/exit 0))))))]

          (register-drag-drop-event scene controller)
          (reset! _window controller)
          (reset! _stage stage))))
    this)

  (stop [this]
    (javafx.application.Platform/runLater
     #(do
        (.close @_window)
        (.close @_stage)))
    this))

(defn dev-new-window []
  (map->Window {:is-dev true
                :_stage (atom nil)
                :_window (atom nil)}))

(defn prod-new-window []
  (map->Window {:is-dev false
                :_stage (atom nil)
                :_window (atom nil)}))
