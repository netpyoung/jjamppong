(ns jjamppong.watcher
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
      (getInputStream)))


(defn cmd->line-seq [cmd]
  (->> cmd
       (cmd->input-stream)
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
      (java.io.BufferedWriter.)))


(defn async->filewriter [ch output-fpath]
  (async/go
    (with-open [writer (fpath->writer output-fpath)]
      (loop []
        (when-let [line (async/<! ch)]
          (println "[wirter]" line)
          (doto writer
            (.write line)
            (.write "\r\n")
            (.flush))
          (recur))))))


(defn async<-lineseq [ch cmd]
  (async/go
    (loop [[fst & rst] (cmd->line-seq cmd)]
      (if (nil? fst)
        nil
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
    [command channel]
  IWatcher
  (run [this async->fn]
    (let [mult (async/mult channel)
          tap-file (async/tap mult (async/chan))
          tap-out (async/tap mult (async/chan))]
      (async->filewriter tap-file (gen-filename))
      (async->fn tap-out)
      (async<-lineseq channel command)))
  (dispose [this]
    (async/close! channel)))


(defn new-watcher []
  (let [command "adb logcat"
        device "192.168.58.101:5555"
        combined-command (str command " " device)
        channel (async/chan)]
    (Watcher. combined-command channel)))
