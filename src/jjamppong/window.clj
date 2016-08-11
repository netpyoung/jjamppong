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
   [javafx.collections.transformation FilteredList]

   [javafx.scene.control Button Alert Alert$AlertType Cell TableColumn TableRow TableCell TableView SelectionMode]
   [javafx.scene.control.cell PropertyValueFactory MapValueFactory]
   [javafx.scene.input KeyCode KeyCodeCombination KeyCombination KeyEvent KeyCombination$Modifier
    Clipboard ClipboardContent]
   [javafx.event ActionEvent EventHandler]
   [javafx.collections FXCollections ListChangeListener]))

(defonce +ONCE+ (javafx.embed.swing.JFXPanel.))




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
  ;; TODO(kep): regex is really usefull?, can you understand at first grace?
  (-> "^(?<timestamp>\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)\\s*(?<pid>\\d+)\\s*(?<tid>\\d+)\\s(?<level>[VDIWEAF])\\s(?<tag>.*?):\\s+(?<message>.*)$"
      (re/re-pattern)
      (re/re-find log)))


(defn logline->Log [log]
  (-> log
      (logline->map)
      (map->Log)))


(def +GLOBAL_LOCK+ (Object.))

(defn async->tableobservable [ch observable]
  (async/go
    (loop []
      (when-let [line (async/<! ch)]
        (locking +GLOBAL_LOCK+
          (->> line
               (logline->Log)
               (.add observable)))
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


(defn ^java.util.function.Predicate f-to-predicate [f]
  ;; https://github.com/clojurewerkz/ogre/blob/master/src/clojure/clojurewerkz/ogre/util.clj
  "Converts a function to java.util.function.Predicate."
  (reify java.util.function.Predicate
    (test [this arg] (f arg))))



(definterface IMainWindowFX
  (close [])

  (^{:tag void} on_btn_start [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_clear [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_stop [^javafx.event.ActionEvent event])
  (^{:tag void} on_check_lvl [^javafx.event.ActionEvent event])
  )


;; (defmacro callback
;;   "Reifies the callback interface."
;;   ;; ref: https://github.com/sonicsmooth/msclojure-junk/blob/master/tableview/src/clojure/tableview/utils.clj#L84
;;   [args & body]
;;   `(reify javafx.util.Callback
;;      (~'call [this# ~@args]
;;       ~@body)))



(defn hello []
  (let [V (javafx.css.PseudoClass/getPseudoClass "V")
        D (javafx.css.PseudoClass/getPseudoClass "D")
        I (javafx.css.PseudoClass/getPseudoClass "I")
        W (javafx.css.PseudoClass/getPseudoClass "W")
        E (javafx.css.PseudoClass/getPseudoClass "E")
        F (javafx.css.PseudoClass/getPseudoClass "F")]
    (reify javafx.util.Callback
      (call [_ table]
        (proxy [TableRow] []
          (updateItem [item empty]
            (proxy-super updateItem item empty)

            (.pseudoClassStateChanged this V false)
            (.pseudoClassStateChanged this D false)
            (.pseudoClassStateChanged this I false)
            (.pseudoClassStateChanged this W false)
            (.pseudoClassStateChanged this E false)
            (.pseudoClassStateChanged this F false)

            (condp = (:level item)
              "V" (.pseudoClassStateChanged this V true)
              "D" (.pseudoClassStateChanged this D true)
              "I" (.pseudoClassStateChanged this I true)
              "W" (.pseudoClassStateChanged this W true)
              "E" (.pseudoClassStateChanged this E true)
              "F" (.pseudoClassStateChanged this F true)
              true)))))))






(deftype MainWindow
    [proc_adb
     table_contents
     filtered
     ^{FXML [] :unsynchronized-mutable true} btn_clear
     ^{FXML [] :unsynchronized-mutable true} btn_start
     ^{FXML [] :unsynchronized-mutable true} btn_stop
     ^{FXML [] :unsynchronized-mutable true} check_lvl_v
     ^{FXML [] :unsynchronized-mutable true} check_lvl_d
     ^{FXML [] :unsynchronized-mutable true} check_lvl_i
     ^{FXML [] :unsynchronized-mutable true} check_lvl_w
     ^{FXML [] :unsynchronized-mutable true} check_lvl_e
     ^{FXML [] :unsynchronized-mutable true} check_lvl_f
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
                                (.setCellValueFactory (MapValueFactory. (keyword x)))))))))

     )



   (println (.getRowFactory table_log))


   (.setRowFactory table_log
                   (hello))
   ;; (.setUseSystemMenuBar menu_bar true)
   (impl/init self)
   )

  impl/IMainWindow
  (init [this]
    (when-let [proc @proc_adb]
      (impl/stop proc)
      (reset! proc_adb nil))
    (.setDisable btn_clear true)
    (.setDisable btn_start false)
    (.setDisable btn_stop true))

  IMainWindowFX
  (close [this]
    (impl/init this)
    )

  (^{:tag void} on_btn_start [this ^javafx.event.ActionEvent event]


   (doto this
     (.on_btn_stop event)
     (.on_btn_clear event))
   (.setDisable btn_clear false)
   (.setDisable btn_start true)
   (.setDisable btn_stop false)
   (doto table_log
     (.setItems filtered)
     ;; (auto-scroll)
     )
   (reset! proc_adb (watcher/new-watcher))
   (let [ch (impl/run @proc_adb)]
     (async->tableobservable ch table_contents)))

  (^{:tag void} on_btn_clear [this ^javafx.event.ActionEvent event]
   (.clear table_contents))

  (^{:tag void} on_btn_stop [this ^javafx.event.ActionEvent event]
   (impl/init this))

  (^{:tag void} on_check_lvl [this ^javafx.event.ActionEvent event]
   (locking +GLOBAL_LOCK+
     (.setPredicate
      filtered
      (f-to-predicate (fn [p]
                        (let [level (:level p)]
                          (condp = level
                            "V" (.isSelected check_lvl_v)
                            "D" (.isSelected check_lvl_d)
                            "I" (.isSelected check_lvl_i)
                            "W" (.isSelected check_lvl_w)
                            "E" (.isSelected check_lvl_e)
                            "F" (.isSelected check_lvl_f)
                            true)))))))
  )




(defn gen-MainWindow []
  (let [observable (FXCollections/observableArrayList [])
        filtered (FilteredList. observable
                                (f-to-predicate (fn [p] true)))]
    (->MainWindow
     (atom nil)                             ;proc_adb
     observable                             ;table_contents
     filtered                               ;filtered
     nil                                    ;btn_clear
     nil                                    ;btn_start
     nil                                    ;btn_stop
     nil                                    ;check_lvl_v
     nil                                    ;check_lvl_d
     nil                                    ;check_lvl_i
     nil                                    ;check_lvl_w
     nil                                    ;check_lvl_e
     nil                                    ;check_lvl_f
     nil                                    ;table_log
     )))


(defrecord Window [is-dev _stage _window]
  component/Lifecycle
  (start [this]
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
