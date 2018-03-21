(ns jjamppong.fx.itm-highlight
  (:gen-class)
  (:require
   [system.repl :refer [system]]
   [com.stuartsierra.component :as component]
   [jjamppong.macros :as m]
   [jjamppong.protocols :as impl]
   [jjamppong.interfaces]
   [jjamppong.watcher :as watcher]
   [clojure.reflect :as r]
   [clojure.string :as str]
   [named-re.re :as re]
   [garden.core :refer [css]]
   [clojure.java.shell]
   [clojure.core.async :as async])

  (:import
   [java.lang Enum]
   [java.net URL]
   [java.util ResourceBundle]

   ;; javafx
   [java.awt.event.InputMethodEvent]
   ;; [java.beans.EventHandler]
   [javafx.beans.property SimpleStringProperty]
   [javafx.beans.value ObservableValue]
   [javafx.fxml FXMLLoader FXML]
   [javafx.stage Stage StageBuilder Modality]
   [javafx.scene Scene]
   [javafx.collections.transformation FilteredList]

   [javafx.scene.control Button Alert Alert$AlertType Cell TableColumn TableRow TableCell TableView SelectionMode ListCell]
   [javafx.scene.control.cell PropertyValueFactory MapValueFactory]
   [javafx.scene.input KeyCode KeyCodeCombination KeyCombination KeyEvent KeyCombination$Modifier
    Clipboard ClipboardContent
    DragEvent]
   [javafx.event ActionEvent EventHandler]
   [javafx.collections FXCollections ListChangeListener]

   [org.controlsfx.control.CheckListView]
   [org.controlsfx.control.IndexedCheckModel]
   [org.controlsfx.control.ListSelectionView]
   ))


(defn map-to->Color [m]
  (let [{:keys [r g b a]} m]
    (javafx.scene.paint.Color. r g b a)))

(defn map-to->ColorCssStr [m]
  (let [{:keys [r g b a]} m]
    (str "rgba(" (int (* r 100))  "%," (int (* g 100)) "%," (int (* b 100)) "%," a ")" )))

(defn map-to->css [m]
  ;; ref: https://docs.oracle.com/javafx/2/api/javafx/scene/doc-files/cssref.html
  (let [{:keys [color-background color-foreground]} m
        fx-font "italic"
        fx-foreground (map-to->ColorCssStr color-foreground)
        fx-background (map-to->ColorCssStr color-background)]
    (css
     [:. {
      :-fx-font fx-font
      :-fx-base fx-background
      :-fx-text-fill fx-foreground
      }])))

(defn register-drag-event [cell]
  ;; ref: http://blog.ngopal.com.np/2012/05/06/javafx-drag-and-drop-cell-in-listview/
  (doto cell
    (.setOnDragDetected
     (reify javafx.event.EventHandler
       (handle [this event]
         )))
    (.setOnDragOver
     (reify javafx.event.EventHandler
       (handle [this event]
         (doto event
           (.acceptTransferModes
            (into-array [javafx.scene.input.TransferMode/COPY]))
           (.consume)))))

    (.setOnDragEntered
     (reify javafx.event.EventHandler
       (handle [this event]

         )))

    (.setOnDragExited
     (reify javafx.event.EventHandler
       (handle [this event]
         )))

    (.setOnDragDropped
     (reify javafx.event.EventHandler
       (handle [this event]
         ))
     )))

(deftype ItmHighlight
    [^{:unsynchronized-mutable true} _item
     ^{FXML [] :unsynchronized-mutable true} root
     ^{FXML [] :unsynchronized-mutable true} check_example
     ^{FXML [] :unsynchronized-mutable true} bg_pane
     ^{FXML [] :unsynchronized-mutable true} lbl_filter_string]

  javafx.fxml.Initializable
  (^{:tag void}
   initialize [self, ^URL fxmlFileLocation, ^ResourceBundle resources]
   )

  jjamppong.interfaces.IItmHighlightFX
  (update1 [this item]
    (set! _item item)
    (doto check_example
      (.setSelected (:is-selected @item))
      )

    (doto lbl_filter_string
      ;; (.setStyle (map-to->css @item))
      (.setText  (str item)))

    (doto bg_pane
      (.setStyle (map-to->css @item)))

    ;; (.removeAll (.getStylesheets root))
    ;; (.add (.getStylesheets root) (map-to->css @item))
    ;; (doto root
    ;;   (.setStyle (map-to->css @item)))
    )

  (^{:tag void} on_check [this ^javafx.event.ActionEvent event]
   (swap! _item assoc :is-selected (.isSelected check_example))))

(defn gen-NodeController []
  (ItmHighlight.
   false                                ;_item
   nil                                  ;root
   nil                                  ;bg_pane
   nil                                  ;check_example
   nil                                  ;lbl_filter_string
   ))

;; FIXME(kep): Oh my eyes!
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
                  (let [controller (gen-NodeController)
                        loader (doto (FXMLLoader. fxml)
                                 (.setController controller))
                        node (.load loader)]
                    (doto this
                      ;; (register-drag-event)
                      (.setUserData
                       {:node node
                        :controller controller}))))
                (let [{:keys [node controller]} (.getUserData this)]
                  (when (nil? (.getGraphic this))
                    (-> this (.setGraphic node)))
                  ;; (println ["FFFFF " (.getAttribute controller :root)])
                  (-> controller (.update1 item)))))))))))
