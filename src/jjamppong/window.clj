(ns jjamppong.window
  (:require
   [system.repl :refer [system]]
   [com.stuartsierra.component :as component]
   [jjamppong.macros :as m]
   [jjamppong.protocols :as impl]
   [jjamppong.watcher :as watcher]
   [clojure.reflect :as r]
   [clojure.string :as str]
   [named-re.re :as re]
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


(def config-atom
  (atom
   {:tags #{"V" "D" "I" "W" "E" "F"}
    :auto-scroll true
    :filter-text ""
    :filter-is-ignorecase false
    :filter-is-regex false
    }))

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
  ;; FIXME(kep): copy clipboard duplicate
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
  (let [m (logline->map log)]
    (if m
      (map->Log m)
      (map->Log {:message log}))))

;; TODO(kep): REMOVE global_lock. need to solve clojure's way.
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
  ;; TODO(kep): is too laggy scrollTo. need to refactoring.
  ;; TODO(kep): disable auto-scroll
  (.. table
      (getItems)
      (addListener
       (reify ListChangeListener
         (onChanged [this change]
           (.next change)
           (when (:auto-scroll @config-atom)
             (let [s (.. table getItems size)]
               (when (pos? s)
                 (javafx.application.Platform/runLater
                  #(do
                     (let [itm (first (.getAddedSubList change))]
                       (when (instance? Log itm)
                         (.scrollTo table itm)))))))))))))


(defn ^java.util.function.Predicate f-to-predicate [f]
  ;; https://github.com/clojurewerkz/ogre/blob/master/src/clojure/clojurewerkz/ogre/util.clj
  "Converts a function to java.util.function.Predicate."
  (reify java.util.function.Predicate
    (test [this arg] (f arg))))

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

(defn init-table [table items]
  (doto table
    (.setEditable false)
    (.. (getSelectionModel)
        (setSelectionMode SelectionMode/MULTIPLE))
    (.. (getColumns)
        (addAll
         (->> (Log/getBasis)
              (mapv (fn [x]
                      (doto (TableColumn. (str x))
                        (.setCellValueFactory (MapValueFactory. (keyword x)))))))))
    (.setOnKeyPressed evt-handler)
    (.setRowFactory (hello))
    (.setItems items)
    (auto-scroll)))


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


(defn ffff [fpath observable]
  (locking +GLOBAL_LOCK+
    (.clear observable)
    (with-open [rdr (clojure.java.io/reader fpath)]
      (doseq [line (line-seq rdr)]
        (->> line
             (logline->Log)
             (.add observable))))))

;; (test-popup (.getWindow (.getScene (.getSource event))))
(defn test-popup [window]
  (let [fxml (clojure.java.io/resource "dialog.fxml")
        ;; controller (gen-MainWindow)
        ;; loader (doto (FXMLLoader. fxml)
        ;;          (.setController controller))
        loader (FXMLLoader. fxml)
        scene (Scene. (.load loader))
        stage (doto (.build (StageBuilder/create))
                (.setScene scene)
                (.setTitle "jjamppong")
                (.initModality Modality/APPLICATION_MODAL)
                (.initOwner window)
                (.showAndWait))]))

(defn get-devices []
  ;; TODO(kep): handle offline
  (->> (clojure.java.shell/sh "adb" "devices")
       :out
       (str/split-lines)
       (filter #(re-matches #"[0-9].*" %))
       (map #(str/replace % #"\tdevice" ""))
       (map #(str/replace % #"\toffline" ""))))

(deftype MainWindow
         [proc_adb
          table_contents
          filtered
          ^{FXML [] :unsynchronized-mutable true} status_bar
          ^{FXML [] :unsynchronized-mutable true} menuitem_scan
          ^{FXML [] :unsynchronized-mutable true} menuitem_run
          ^{FXML [] :unsynchronized-mutable true} list_devices
          ^{FXML [] :unsynchronized-mutable true} btn_clear
          ^{FXML [] :unsynchronized-mutable true} btn_start
          ^{FXML [] :unsynchronized-mutable true} btn_stop
          ^{FXML [] :unsynchronized-mutable true} check_lvl_v
          ^{FXML [] :unsynchronized-mutable true} check_lvl_d
          ^{FXML [] :unsynchronized-mutable true} check_lvl_i
          ^{FXML [] :unsynchronized-mutable true} check_lvl_w
          ^{FXML [] :unsynchronized-mutable true} check_lvl_e
          ^{FXML [] :unsynchronized-mutable true} check_lvl_f
          ^{FXML [] :unsynchronized-mutable true} txt_filter
          ^{FXML [] :unsynchronized-mutable true} table_log]

  javafx.fxml.Initializable
  (^{:tag void}
    initialize [self, ^URL fxmlFileLocation, ^ResourceBundle resources]
    (init-table table_log filtered)
   ;; (.setUseSystemMenuBar menu_bar true)
    (impl/init self))

  impl/IMainWindow
  (init [this]
    (when-let [proc @proc_adb]
      (impl/stop proc)
      (reset! proc_adb nil))
    (.setDisable btn_clear true)
    (.setDisable btn_start false)
    ;; (.setDisable btn_stop true)

    (.setAccelerator menuitem_scan
                     (KeyCodeCombination. KeyCode/E (into-array KeyCombination$Modifier [KeyCombination/CONTROL_DOWN])))
    (.setAccelerator menuitem_run
                     (KeyCodeCombination. KeyCode/R (into-array KeyCombination$Modifier [KeyCombination/CONTROL_DOWN])))
)

  (load [this fpath]
    (impl/init this)
    (impl/update-status-message this fpath)
    (ffff fpath table_contents))

  (update-status-message [this message]
    (.setText status_bar message))

  (get-table [this]
    table_log)

  (update-predicate [this filter-list]
    ;; TODO(kep): too long. need to refactoring
    (.setPredicate
     filter-list
     (f-to-predicate
      (fn [p]
        (let [{:keys [level message]} p
              {:keys [tags filter-text filter-is-regex filter-is-ignorecase]} @config-atom]
          (if-not (get tags level)
            false
            (if filter-is-regex
              (if filter-is-ignorecase
                (some? (re-find (re-pattern (str "(?i)" filter-text)) message))
                (some? (re-find (re-pattern filter-text) message)))
              (if filter-is-ignorecase
                (str/includes? (str/lower-case message) (str/lower-case filter-text))

                (str/includes? message filter-text)))))))))

  ;; wtf this ugly interface declare
  jjamppong.protocols.IMainWindowFX
  (close [this]
    (impl/init this))

  (^{:tag void}
   on_btn_scan [this ^javafx.event.ActionEvent event]
   (doto list_devices
     (.setItems (FXCollections/observableArrayList (get-devices))))
   (.selectFirst
     (.getSelectionModel list_devices)))

  (^{:tag void}
   on_btn_start [this ^javafx.event.ActionEvent event]
   (when-let [device (.getSelectedItem (.getSelectionModel list_devices))]
     (doto this
       (.on_btn_stop event)
       (.on_btn_clear event))
     (.setDisable btn_clear false)
     (.setDisable btn_start true)
     (.setDisable btn_stop false)
     (impl/update-status-message this device)
     (reset! proc_adb (watcher/new-watcher device))
     (-> (impl/run @proc_adb)
         (async->tableobservable table_contents))))

  (^{:tag void}
   on_btn_clear [this ^javafx.event.ActionEvent event]
    (.clear table_contents))

  (^{:tag void}
   on_btn_stop [this ^javafx.event.ActionEvent event]
   (impl/init this))

  (^{:tag void}
   on_check_lvl [this ^javafx.event.ActionEvent event]
   (let [event-source ^javafx.scene.control.CheckBox (.getSource event)]
     (defonce check-dic
       {check_lvl_v "V"
        check_lvl_d "D"
        check_lvl_i "I"
        check_lvl_w "W"
        check_lvl_e "E"
        check_lvl_f "F"
        })
     (let [tag (get check-dic event-source)]
       (if (.isSelected event-source)
         (swap! config-atom update-in [:tags] conj tag)
         (swap! config-atom update-in [:tags] disj tag))))
   (impl/update-predicate this filtered))

  (^{:tag void}
   on_txt_filter_changed [this ^javafx.scene.input.KeyEvent event]
   (swap! config-atom assoc :filter-text  (.getText (.getSource event)))
   (impl/update-predicate this filtered))

  (^{:tag void}
   on_check_ignorecase [this ^javafx.event.ActionEvent event]
   (let [event-source (.getSource event)]
     (swap! config-atom assoc :filter-is-ignorecase (.isSelected event-source)))
   (impl/update-predicate this filtered))

  (^{:tag void}
   on_check_regex [this ^javafx.event.ActionEvent event]
   (let [event-source (.getSource event)]
     (swap! config-atom assoc :filter-is-regex (.isSelected event-source)))
   (impl/update-predicate this filtered))
)

(defn gen-MainWindow []
  (let [observable (FXCollections/observableArrayList [])
        filtered (FilteredList. observable
                                (f-to-predicate (fn [p] true)))]
    (->MainWindow
     (atom nil)                             ;proc_adb
     observable                             ;table_contents
     filtered                               ;filtered
     nil                                    ;status_bar
     nil                                    ;menuitem_scan
     nil                                    ;menuitem_run
     nil                                    ;list_devices
     nil                                    ;btn_clear
     nil                                    ;btn_start
     nil                                    ;btn_stop
     nil                                    ;check_lvl_v
     nil                                    ;check_lvl_d
     nil                                    ;check_lvl_i
     nil                                    ;check_lvl_w
     nil                                    ;check_lvl_e
     nil                                    ;check_lvl_f
     nil                                    ;txt_filter
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
