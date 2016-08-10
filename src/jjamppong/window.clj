(ns jjamppong.window
  (:require
   [system.repl :refer [system]]
   [com.stuartsierra.component :as component]
   [jjamppong.macros :as m]
   [jjamppong.protocols :as impl]
   [jjamppong.watcher :as watcher]
   [clojure.reflect :as r]
   [named-re.re :as re]
   [clojure.core.async :as async])

  (:import
   [java.lang Enum]
   [java.net URL]
   [java.util ResourceBundle]

   ;; javafx
   [javafx.beans.property SimpleStringProperty]
   [javafx.fxml FXMLLoader FXML]
   [javafx.stage Stage StageBuilder]
   [javafx.scene Scene]
   [javafx.scene.control Button Alert Alert$AlertType TableColumn TableView SelectionMode]
   [javafx.scene.control.cell PropertyValueFactory MapValueFactory]
   [javafx.scene.input KeyCode KeyCodeCombination KeyCombination KeyEvent KeyCombination$Modifier
    Clipboard ClipboardContent]
   [javafx.event ActionEvent EventHandler]
   [javafx.collections FXCollections ListChangeListener]))


(defn alert [title header content]
  (let [alert (Alert. Alert$AlertType/INFORMATION)]
    (doto alert
      (.setTitle title)
      (.setHeaderText header)
      (.setContentText content)
      (.showAndWait))))


(defn obslist [& items]
  (-> (into-array items)
      (FXCollections/observableArrayList)))


(defn event-handler*
  [f]
  (reify javafx.event.EventHandler
    (handle [this e] (f e))))


(defn get-selected-cell-strings [^TableView table]
  (let [items (.getItems table)]
    (->> (.. table getSelectionModel getSelectedIndices)
         (map #(str (.get items %)))
         (clojure.string/join "\n"))))


(let [copyKeyCodeCompination
      (KeyCodeCombination. KeyCode/C (into-array KeyCombination$Modifier [KeyCombination/SHORTCUT_DOWN]))]
  (def evt-handler
    (event-handler*
     (fn [^KeyEvent e]
       (when (.match copyKeyCodeCompination e)
         (let [table (.getSource e)]
           (.. (Clipboard/getSystemClipboard)
               (setContent (doto (ClipboardContent.)
                             (.putString (get-selected-cell-strings table)))))))))))


(defrecord Log
    [timestamp pid tid level tag message]
  Object
  (toString [this]
    (->> this
         (vals)
         (clojure.string/join "\t"))))

;; (defn logline->map [log]
;;   (zipmap [:time :pid :tid :level :tag :text]
;;           (-> (str "^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+"
;;                    "(\\d+)\\s+(\\d+)\\s+([A-Z])\\s+"
;;                    "(.+?)\\s*: (.*)$")
;;               (re-pattern)
;;               (re-find log)
;;               (rest))))



    ;; brief:      XRegExp("^(?<level>[VDIWEAF])\\/(?<tag>[^)]{0,23}?)\\(\\s*(?<pid>\\d+)\\):\\s+(?<message>.*)$"),
    ;; threadtime: XRegExp("^(?<timestamp>\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)\\s*(?<pid>\\d+)\\s*(?<tid>\\d+)\\s(?<level>[VDIWEAF])\\s(?<tag>.*?):\\s+(?<message>.*)$"),
    ;; time:       XRegExp("^(?<timestamp>\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+):*\\s(?<level>[VDIWEAF])\\/(?<tag>.*?)\\((?<pid>\\s*\\d+)\\):\\s+(?<message>.*)$"),
    ;; process:    XRegExp("^(?<level>[VDIWEAF])\\(\\s*(?<pid>\\d+)\\)\\s+(?<message>.*)$"),
    ;; tag:        XRegExp("^(?<level>[VDIWEAF])\\/(?<tag>[^)]{0,23}?):\\s+(?<message>.*)$"),
    ;; thread:     XRegExp("^(?<level>[VDIWEAF])\\(\\s*(?<pid>\\d+):(?<tid>0x.*?)\\)\\s+(?<message>.*)$"),
    ;; ddms_save:  XRegExp("^(?<timestamp>\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+):*\\s(?<level>VERBOSE|DEBUG|ERROR|WARN|INFO|ASSERT)\\/(?<tag>.*?)\\((?<pid>\\s*\\d+)\\):\\s+(?<message>.*)$")

(defn logline->map [log]
  ;; ref: https://github.com/mcginty/logcat-parse/blob/master/src/logcat-parse.coffee
  (-> "^(?<timestamp>\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)\\s*(?<pid>\\d+)\\s*(?<tid>\\d+)\\s(?<level>[VDIWEAF])\\s(?<tag>.*?):\\s+(?<message>.*)$"
      (re/re-pattern)
      (re/re-find log)))


(defn logline->Log [log]
  (-> log
      (logline->map)
      (map->Log)))


(defn async->tableobservable [ch observable]
  (async/go
    (loop []
      (when-let [line (async/<! ch)]
        (->> line
             (logline->Log)
             (.add observable))
        (recur)))))


(defn auto-scroll [table]
  ;; i don't know why it isn't working
  (.. table
      (getItems)
      (addListener
       (reify ListChangeListener
         (onChanged [this change]
           (.next change)
           (let [size (.. table getItems size)]
             (when (pos? size)
               (.scrollTo table (- size 1)))))))))


(definterface IMainWindowFX
  (close [])
  (^{:tag void} on_btn_start [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_clear [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_stop [^javafx.event.ActionEvent event]))


(deftype MainWindow
    [proc_adb
     table_contents
     ^{FXML [] :unsynchronized-mutable true} btn_start
     ^{FXML [] :unsynchronized-mutable true} table_log]

  javafx.fxml.Initializable
  (^{:tag void}
   initialize [self, ^URL fxmlFileLocation, ^ResourceBundle resources]
   (doto table_log
     (.setEditable true)
     (.. (getSelectionModel)
         (setSelectionMode SelectionMode/MULTIPLE))
     (.setOnKeyPressed evt-handler)

     (.. (getColumns)
         (addAll (->> (Log/getBasis)
                      (mapv (fn [x]
                              (doto (TableColumn. (str x))
                                (.setCellValueFactory (MapValueFactory. (keyword x))))))))))



   ;; (.setUseSystemMenuBar menu_bar true)
   )

  IMainWindowFX
  (close [this]
    (.on_btn_stop this nil))
  (^{:tag void} on_btn_start [this ^javafx.event.ActionEvent event]
   (doto this
     (.on_btn_stop event)
     (.on_btn_clear event))
   ;; (.setDisable btn_start true)
   (doto table_log
     (.setItems table_contents)
     ;; (auto-scroll)
     )
   (reset! proc_adb (watcher/new-watcher))
   (impl/run @proc_adb
     #(async->tableobservable % table_contents)))

  (^{:tag void} on_btn_clear [this ^javafx.event.ActionEvent event]
   (.clear table_contents))

  (^{:tag void} on_btn_stop [this ^javafx.event.ActionEvent event]
   (when-let [proc @proc_adb]
     (impl/stop proc)
     (reset! proc_adb nil))
   (.setDisable btn_start false)))




(defn gen-MainWindow []
  (->MainWindow
   (atom nil)                             ;proc_adb
   (FXCollections/observableArrayList []) ;table_contents
   nil                                    ;btn_start
   nil                                    ;table_log
   ))


(defrecord Window [is-dev _stage _window]
  component/Lifecycle
  (start [this]
    (javafx.embed.swing.JFXPanel.)
    (javafx.application.Platform/setImplicitExit false)
    (javafx.application.Platform/runLater
     #(do
        (let [fxml (clojure.java.io/resource "layout.fxml")
              controller (gen-MainWindow)
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
