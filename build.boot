(set-env!
 :source-paths #{"src"}
 :resource-paths #{"resources"}
 :dependencies
 '[[org.clojure/clojure "1.9.0-alpha10"]
   [org.clojars.ato/clojure-jsr223 "1.5.1"]

   ;; core
   [environ "1.0.3"]
   [org.danielsz/system "0.3.1"]
   [funcool/beicon "2.2.0"]
   [org.clojure/core.async "0.2.385"]
   [named-re "1.0.0"]
   [org.controlsfx/controlsfx "8.40.11"]


   ;; boot
   [tolitius/boot-check "0.1.3" :scope "test"]
   [cljfmt "0.5.3" :scope "test"]
   [boot-environ "1.0.3" :scope "test"]
   [adzerk/boot-jar2bin "1.1.0" :scope "test"]
   [adzerk/bootlaces "0.1.13" :scope "test"]])


(require
 '[tolitius.boot-check :as check]
 '[cljfmt.core :as fmt]
 '[clojure.java.io :as io]

 '[environ.boot :refer [environ]]
 '[adzerk.bootlaces :refer :all]
 '[system.boot :refer [system]]
 '[jjamppong.systems :refer [dev-system]]
 '[adzerk.boot-jar2bin :refer [bin exe]])


(def +version+ "0.0.1-SNAPSHOT")
(bootlaces! +version+)


;; ref: https://gist.github.com/bartojs/83a096ecb1221885ddd1

(defn fmt-file [f]
  (println "formatting" (.getName f))
  (spit f (fmt/reformat-string (slurp f))))

(defn clj-file? [f]
  (and (.exists f) (.isFile f) (not (.isHidden f))
       (contains? #{"clj" "cljs" "cljc" "cljx" "boot"}
                  (last (.split (.toLowerCase (.getName f)) "\\.")))))

(deftask fmt [f files VAL str "file(s) to format"]
  (let [f (io/file files)]
    (when (.exists f)
      (doall (map fmt-file (filter clj-file? (if (.isDirectory f) (file-seq f) [f])))))))



(deftask check []
  (comp
   (check/with-yagni)
   (check/with-eastwood)
   (check/with-kibit)
   (check/with-bikeshed)))


(deftask dev
  "Run a restartable system in the Repl"
  []
  (comp
   (watch)
   (system :sys #'dev-system
           :auto true
           :files ["window.clj" "layout.fxml" "layout.css"])
   (repl :server true :init-ns 'jjamppong.core)))


(deftask build
  "Create a standalone jar file."
  []
  (comp
   (aot :namespace '#{jjamppong.core})
   (pom :project 'jjamppong
        :version +version+
        :description "jjamppong")
   (uber)
   (jar :main 'jjamppong.core)
   (target)
   ;; (bin :output-dir "bin")
   ))
