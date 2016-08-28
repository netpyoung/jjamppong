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


(deftype ItmHighlight
    [^{:unsynchronized-mutable true} _item
     ^{FXML [] :unsynchronized-mutable true} root
     ^{FXML [] :unsynchronized-mutable true} check_example
     ^{FXML [] :unsynchronized-mutable true} lbl_filter_string]

  javafx.fxml.Initializable
  (^{:tag void}
   initialize [self, ^URL fxmlFileLocation, ^ResourceBundle resources]
   )

  jjamppong.protocols.ItmHighlightFX
  (update1 [this item]
    (set! _item item)
    (doto check_example
      (.setSelected (@item :is-selected))
      )

    (doto lbl_filter_string
      (.setStyle (map-to->css @item))
      (.setText  (str item)))
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
   nil                                  ;check_example
   nil                                  ;lbl_filter_string
   ))

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
                  (-> controller (.update1 item)))))))))))



(defn init-listview [listview items]
  (doto listview
    (.setItems items)
    (.setCellFactory (gen-cellfactory))
    (-> (.getSelectionModel)
        (.setSelectionMode SelectionMode/MULTIPLE))))




(defn color->map [^javafx.scene.paint.Color color]
  {:r (.getRed color)
   :g (.getGreen color)
   :b (.getBlue color)
   :a (.getOpacity color)})

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
   (init-listview list_highlight @atom_table_contents))

  jjamppong.protocols.IHighlightWindowFX
  (hello [this]
    (map deref @atom_table_contents))

  (^{:tag void} on_btn_new [this ^javafx.event.ActionEvent event]
   (-> @atom_table_contents
       (.add (atom
              {:is-selected      true
               :filter-string    (.getText txt_filter_string)
               :color-background (color->map (.getValue color_background))
               :color-foreground  (color->map (.getValue color_foreground))
               :is-regex         (.isSelected check_regex)})))
   (.refresh list_highlight))

  (^{:tag void} on_btn_remove [this ^javafx.event.ActionEvent event]
   (let [items (-> list_highlight .getItems)
         selected (-> list_highlight
                      .getSelectionModel
                      .getSelectedItems)]
     (-> items (.removeAll selected))
     (.refresh list_highlight)))

  (^{:tag void} on_btn_up [this ^javafx.event.ActionEvent event]
   (let [model (-> list_highlight .getSelectionModel)
         index (-> model .getSelectedIndex)]
     (when (pos? index)
       (let [items (-> list_highlight .getItems)
             next (-> items (.get (dec index)))]
         (doto items
           (.remove next)
           (.add index next))
         (.refresh list_highlight)))))

  (^{:tag void} on_btn_down [this ^javafx.event.ActionEvent event]
   (let [model (-> list_highlight .getSelectionModel)
         index (-> model .getSelectedIndex)
         items (-> list_highlight .getItems)]
     (when (< index (dec (.size items)))
       (let [next (-> items (.get (inc index)))]
         (doto items
           (.remove next)
           (.add index next))
         (.refresh list_highlight))))))

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
  ;; TODO(kep): remove string hard coding.
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


;; ======
(-> {:is-selected      true
     :filter-string    "test"
     :color-background {:r 1.0 :g 1.0 :b 1.0 :a 1.0}
     :color-foreground  {:r 1.0 :g 1.0 :b 1.0 :a 1.0}
     :is-regex         false}
    map-to->css)
