(ns jjamppong.window.highlight-window
  (:require
   [system.repl :refer [system]]
   [com.stuartsierra.component :as component]
   [jjamppong.macros :as m]
   [jjamppong.protocols :as impl]
   [jjamppong.watcher :as watcher]
   [clojure.reflect :as r]
   [clojure.string :as str]
   [named-re.re :as re]
   [clojure.java.shell]
   [clojure.core.async :as async])


  (:import
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

   [javafx.scene.control Button Alert Alert$AlertType Cell TableColumn TableRow TableCell TableView SelectionMode ListCell]
   [javafx.scene.control.cell PropertyValueFactory MapValueFactory]
   [javafx.scene.input KeyCode KeyCodeCombination KeyCombination KeyEvent KeyCombination$Modifier
    Clipboard ClipboardContent]
   [javafx.event ActionEvent EventHandler]
   [javafx.collections FXCollections ListChangeListener]

   [org.controlsfx.control.CheckListView]
   [org.controlsfx.control.IndexedCheckModel]
   [org.controlsfx.control.ListSelectionView]
   ))

(defonce +ONCE+ (javafx.embed.swing.JFXPanel.))

(definterface ItmHighlightFX
  (update1 [item])
  (^{:tag void} on_check [^javafx.event.ActionEvent event])
  )


(deftype ItmHighlight
    [^{:unsynchronized-mutable true} _item
     ^{FXML [] :unsynchronized-mutable true} check_example
     ^{FXML [] :unsynchronized-mutable true} lbl_filter_string
     ]
  javafx.fxml.Initializable
  (^{:tag void}
   initialize [self, ^URL fxmlFileLocation, ^ResourceBundle resources]
   )

  ItmHighlightFX
  (update1 [this item]
    (set! _item item)
    (.setSelected check_example (@item :is-selected))
    (.setText lbl_filter_string (str item)))

  (^{:tag void} on_check [this ^javafx.event.ActionEvent event]
   (swap! _item assoc :is-selected (.isSelected check_example))))


(defn gen-NodeController []
  (ItmHighlight. false nil nil))


(let [fxml (clojure.java.io/resource "itm_highlight.fxml")]
  (defn gen-cellfactory []
    (reify javafx.util.Callback
      (call [_ param]
        (proxy [javafx.scene.control.ListCell] []
          (updateItem [item is-empty]
            (proxy-super updateItem item is-empty)
            (if (or is-empty (nil? item))
              (.setGraphic this nil)
              (do
                (when (nil? (.getUserData this))
                  ;; (println "BBB:" (bean this))
                  (.setUserData this 1)
                  (let [controller (gen-NodeController)
                        loader (doto (FXMLLoader. fxml)
                                 (.setController controller))
                        node (.load loader)]
                    (.setUserData this {:node node :controller controller})))
                (when (nil? (.getGraphic this))
                  (let [{:keys [node controller]} (.getUserData this)]
                    (-> this (.setGraphic node))
                    (.update1 controller item)))))))))))



(defn eeinit-listview [listview items]
  (doto listview
    (.setItems items)
    (.setCellFactory (gen-cellfactory))
    (.. (getSelectionModel)
        (setSelectionMode SelectionMode/MULTIPLE))))

(definterface IHighlightWindowFX
  (hello [])
  (^{:tag void} on_btn_new [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_remove [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_up [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_down [^javafx.event.ActionEvent event])
  )


(deftype HighlightWindow
    [
     atom_table_contents
     ^{FXML [] :unsynchronized-mutable true} list_highlight
     ^{FXML [] :unsynchronized-mutable true} btn_new
     ^{FXML [] :unsynchronized-mutable true} btn_remove
     ^{FXML [] :unsynchronized-mutable true} btn_up
     ^{FXML [] :unsynchronized-mutable true} btn_down
     ^{FXML [] :unsynchronized-mutable true} check_regex
     ^{FXML [] :unsynchronized-mutable true} txt_filter_string
     ^{FXML [] :unsynchronized-mutable true} color_background
     ^{FXML [] :unsynchronized-mutable true} color_foreground
     ]

  javafx.fxml.Initializable
  (^{:tag void}
   initialize [self, ^URL fxmlFileLocation, ^ResourceBundle resources]
   (println "INIT INIt INIT")
   (doto list_highlight
     (eeinit-listview @atom_table_contents)))

  IHighlightWindowFX
  (hello [this]
    (->> @atom_table_contents
         (map deref)
         ))
  (^{:tag void} on_btn_new [this ^javafx.event.ActionEvent event]
   (.. @atom_table_contents
       (add (atom
             {:is-selected true
              :filter-string (.getText txt_filter_string)
              :color-background (let [cc (.getValue color_background)]
                                  {:r (.getRed cc)
                                   :g (.getGreen cc)
                                   :b (.getBlue cc)
                                   :a (.getOpacity cc)})
              :color-forground (let [cc (.getValue color_foreground)]
                                  {:r (.getRed cc)
                                   :g (.getGreen cc)
                                   :b (.getBlue cc)
                                   :a (.getOpacity cc)})
              :is-regex (.isSelected check_regex)})))
   )


  (^{:tag void} on_btn_remove [this ^javafx.event.ActionEvent event]
   (let [items (.. list_highlight getItems)
         selected (.. list_highlight
                      getSelectionModel
                      getSelectedItems)]
     (println "[[[]]]" [(count selected) (count items)])
     (.. items (removeAll selected))))

  (^{:tag void} on_btn_up [this ^javafx.event.ActionEvent event]
   (let [selected (.. list_highlight
                      getSelectionModel
                      getSelectedItem)]
     ))

  (^{:tag void} on_btn_down [this ^javafx.event.ActionEvent event]
   (let [selected (.. list_highlight
                      getSelectionModel
                      getSelectedItem)]
     ))
  )


(defn gen-HighlightWindow []
  (let [observable (FXCollections/observableArrayList [])]
    (HighlightWindow.
     (atom observable)
     nil                                ;list_highlight
     nil                                ; btn_new
     nil                                ; btn_remove
     nil                                ; btn_up
     nil                                ; btn_down
     nil                                ; check_regex
     nil                                ; txt_filter_string
     nil                                ; color_background
     nil                                ; color_foreground
     )))

(defn test-popup [window]
  (let [fxml (clojure.java.io/resource "highlight.fxml")
        controller (gen-HighlightWindow)
        loader (doto (FXMLLoader. fxml)
                 (.setController controller))
        scene (Scene. (.load loader))
        stage (doto (.build (StageBuilder/create))
                (.setScene scene)
                (.setTitle "jjamppong")
                (.initModality Modality/APPLICATION_MODAL)
                (.initOwner window)
                (.showAndWait))]
    (.hello controller)))
