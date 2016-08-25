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
  (update [item])
  (^{:tag void} on_check [^javafx.event.ActionEvent event])
  )

(deftype ItmHighlight
    [
     ^{FXML [] :unsynchronized-mutable true} check_example
     ^{FXML [] :unsynchronized-mutable true} lbl_filter_string
     ]
  javafx.fxml.Initializable
  (^{:tag void}
   initialize [self, ^URL fxmlFileLocation, ^ResourceBundle resources]
   (println [self, fxmlFileLocation, resources])
   )
  ItmHighlightFX
  (update [this item]
    (println "FFFFF" [this item]))
  (^{:tag void} on_check [this ^javafx.event.ActionEvent event]
   (println "check!!"))
  )


(defn gen-NodeController []
  (ItmHighlight. nil nil))

(let [fxml (clojure.java.io/resource "itm_highlight.fxml")]
  (defn get-cellfactory []
    (let [is-initialized (atom false)]
      (reify javafx.util.Callback
        (call [_ param]
          (proxy [javafx.scene.control.ListCell] []
            (updateItem [item is-empty]
              (proxy-super updateItem item is-empty)
              (if (or is-empty (nil? item))
                (.setGraphic this nil)
                (if (or (not @is-initialized) (nil? (.getGraphic this)))
                  (let [
                        controller (gen-NodeController)
                        loader (doto (FXMLLoader. fxml)
                                 (.setController controller))
                        node (.load loader)]
                    (.. this (setGraphic node))
                    (.. controller (update item))
                    (println "init"))
                  (.. this (update item)))))))))))

(defn eeinit-listview [listview items]
  (doto listview
    (.setCellFactory (get-cellfactory))
    (.setItems items)
    (.. (getSelectionModel)
        (setSelectionMode SelectionMode/MULTIPLE))))

(definterface IHighlightWindowFX
  (^{:tag void} on_btn_add [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_remove [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_up [^javafx.event.ActionEvent event])
  (^{:tag void} on_btn_down [^javafx.event.ActionEvent event])
  )

(deftype HighlightWindow
    [
     atom_table_contents
     ^{FXML [] :unsynchronized-mutable true} list_highlight
     ^{FXML [] :unsynchronized-mutable true} btn_add
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
   (doto list_highlight
     (eeinit-listview @atom_table_contents))

   )
  IHighlightWindowFX
  (^{:tag void} on_btn_add [this ^javafx.event.ActionEvent event]
   (.add @atom_table_contents 1))

  (^{:tag void} on_btn_remove [this ^javafx.event.ActionEvent event]
   (let [items (.. list_highlight
                   getSelectionModel
                   getSelectedItems)]
     (.. list_highlight getItems (removeAll items))
     (.. list_highlight
         getSelectionModel
         clearSelection)

     ))

  (^{:tag void} on_btn_up [this ^javafx.event.ActionEvent event]
   ;; (.add @atom_table_contents 1)

   )
  (^{:tag void} on_btn_down [this ^javafx.event.ActionEvent event]
   ;; (.add @atom_table_contents 1)

   )
  )

(defn gen-HighlightWindow []
  (let [observable (FXCollections/observableArrayList [])]
    (HighlightWindow.
     (atom observable)
     nil                                ;list_highlight
     nil                                ; btn_add
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
                (.showAndWait))]))
