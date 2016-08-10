(ns jjamppong.watcher
  (:import [java.lang ProcessBuilder])
  (:require
   [system.repl :refer [system]]
   [com.stuartsierra.component :as component]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.core.async :as async]))


(defn cmd->input-stream [cmd]
  (.. Runtime
      (getRuntime)
      (exec cmd)
      (getInputStream))
  ;; (-> (ProcessBuilder. ["adb" "logcat" "192.168.58.101:5555"])
  ;;     (.start)
  ;;     (.getInputStream)
  ;;     )
  ;; (clojure.java.io/reader))
  )


(defn cmd->line-seq [cmd]
  (->> cmd
       (cmd->input-stream)
       ((fn [x] (println cmd " " x) x))
       (java.io.InputStreamReader.)
       (java.io.BufferedReader.)
       (line-seq)))


(defn get-devices []
  (->> "adb devices"
       (cmd->line-seq)
       (filter #(re-matches #"[0-9].*" %))
       (map #(str/replace % #"\tdevice" ""))
       (map #(str/replace % #"\toffline" ""))))


(defn fpath->writer [fpath]
  (-> fpath
      (java.io.FileOutputStream.)
      (java.io.OutputStreamWriter. "UTF-8")
      (java.io.BufferedWriter.))
  ;; (-> fpath
  ;;     (clojure.java.io/writer))
  )


(defn async->filewriter [ch output-fpath]
  (async/go
    (with-open [writer (fpath->writer output-fpath)]
      (loop []
        (.flush writer)
        (when-let [line (async/<! ch)]
          (doto writer
            (.write (str line "\r\n")))
          (recur))))))


(defn async<-lineseq [ch cmd]
  (async/go
    (loop [[fst & rst] (cmd->line-seq cmd)]
      (if (nil? fst)
        (async/close! ch)
        (do
          (async/>! ch fst)
          (recur rst))))))


(defn gen-filename []
  (-> "yyyyMMdd_HHmmss"
      (java.text.SimpleDateFormat.)
      (.format (java.util.Date.))
      (str ".log")))


(defprotocol IWatcher
  (run [this async->fn])
  (dispose [this]))


(deftype Watcher
    [command
     ^:unsynchronized-mutable channel]
  IWatcher
  (run [this async->fn]
    (when channel
      (dispose this))
    (set! channel (async/chan 100))
    (let [mult (async/mult channel)
          tap-file (async/tap mult (async/chan 100))
          tap-out (async/tap mult (async/chan 100))
          filename (gen-filename)]
      (async->fn tap-out)
      (async->filewriter tap-file filename)
      (async<-lineseq channel command)
      (println "running" filename)))
  (dispose [this]
    (async/close! channel)
    (set! channel nil)))


(defn new-watcher []
  (let [command "adb logcat"
        device "192.168.58.101:5555"
        combined-command (str command " " device)
        channel nil]
    (Watcher. combined-command channel)))
