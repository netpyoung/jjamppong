(ns jjamppong.watcher
  (:import
   [java.lang ProcessBuilder]
   [java.nio.file FileSystems]
   [java.nio.file Files Paths OpenOption StandardOpenOption])
  (:require
   [jjamppong.protocols :as impl]
   [system.repl :refer [system]]
   [com.stuartsierra.component :as component]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.core.async :as async]))

(defn fpath->writer [fpath]
  (-> (FileSystems/getDefault)
      (.getPath fpath (make-array String 0))
      (Files/newOutputStream
       (into-array
        OpenOption
        [StandardOpenOption/CREATE StandardOpenOption/APPEND]))
      (java.io.BufferedOutputStream.)
      (clojure.java.io/writer)))

(defn async->filewriter [ch output-fpath]
  (async/go
    (with-open [writer (fpath->writer output-fpath)]
      (loop []
        (when-let [line (async/<! ch)]
          (doto writer
            (.write (str line "\r\n"))
            (.flush))
          (recur))))))

(defn async<-lineseq [ch cmd]
  (let [pb (ProcessBuilder. cmd)
        proc (.start pb)
        in (.getInputStream proc)
        out (.getOutputStream proc)
        err (.getErrorStream proc)
        reader (clojure.java.io/reader in)]
    (async/go
      (loop [[fst & rst] (line-seq reader)]
        (if (nil? fst)
          (do
            ;; NOTE(kep): i don't know, is this really need?
            (.close in)
            (.close out)
            (.close err))
          (do
            (async/>! ch fst)
            (recur rst)))))
    proc))

(defn gen-filename []
  (-> "yyyyMMdd_HHmmss"
      (java.text.SimpleDateFormat.)
      (.format (java.util.Date.))
      (str ".log")))

(deftype Watcher
         [command
          ^:unsynchronized-mutable channel
          ^:unsynchronized-mutable proc]
  impl/IWatcher
  (run [this]
    (when channel
      (impl/stop this))
    (set! channel (async/chan))
    (let [mult (async/mult channel)
          tap-file (async/tap mult (async/chan))
          tap-out (async/tap mult (async/chan))]
      (async->filewriter tap-file (gen-filename))
      (set! proc (async<-lineseq channel command))
      tap-out))
  (stop [this]
    (.destroyForcibly proc)
    (async/close! channel)
    (set! channel nil)))

(defn new-watcher [device]
  (let [command "adb"
        combined-command [command "logcat" device]
        channel nil
        proc nil]
    (Watcher. combined-command channel proc)))
