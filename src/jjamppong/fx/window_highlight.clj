(ns jjamppong.fx.window-highlight
  (:gen-class)
  (:require
   [system.repl :refer [system]]
   [com.stuartsierra.component :as component]
   [jjamppong.fx.itm-highlight :as itm-highlight]
   [jjamppong.interfaces]
   )

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


(defn init-listview [listview items]
  (doto listview
    (.setItems items)
    (.setCellFactory (itm-highlight/gen-cellfactory))
    (-> (.getSelectionModel)
        (.setSelectionMode SelectionMode/MULTIPLE))))

(defn color->map [^javafx.scene.paint.Color color]
  {:r (.getRed color)
   :g (.getGreen color)
   :b (.getBlue color)
   :a (.getOpacity color)})

(defrecord FilterItem
    [is-selected
     filter-string
     color-background
     color-foreground
     is-regex])
;;items [(map->FilterItem {:is-selected true, :filter-string "", :color-background {:r 1.0, :g 1.0, :b 1.0, :a 1.0}, :color-foreground {:r 1.0, :g 1.0, :b 1.0, :a 1.0}, :is-regex false})]


(deftype WindowHighlight
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

  jjamppong.interfaces.IWindowHighlightFX
  (hello [this]
    (->> @atom_table_contents
         (map deref )
         (mapv map->FilterItem)))

  (^{:tag void} on_btn_new [this ^javafx.event.ActionEvent event]
   (-> @atom_table_contents
       (.add (atom
              (map->FilterItem
{:is-selected      true
               :filter-string    (.getText txt_filter_string)
               :color-background (color->map (.getValue color_background))
               :color-foreground  (color->map (.getValue color_foreground))
               :is-regex         (.isSelected check_regex)}))))
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

(defn gen-window-highlight [filter-items]
  (let [observable (->> filter-items
                        (mapv atom)
                        FXCollections/observableArrayList)]
    (WindowHighlight.
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


(defn test-popup [window filter-items]
  ;; TODO(kep): remove string hard coding.
  (let [fxml (clojure.java.io/resource "window_highlight.fxml")
        controller (gen-window-highlight filter-items)
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
