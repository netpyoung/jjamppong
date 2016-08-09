(set-env!
 :source-paths #{"src"}
 :resource-paths #{"resources"}
 :dependencies
 '[[org.clojure/clojure "1.9.0-alpha10"]
   [org.clojars.ato/clojure-jsr223 "1.5.1"]

   ;; core
   [environ "1.0.3"]
   [org.danielsz/system "0.3.0"]
   [funcool/beicon "2.2.0"]
   [org.clojure/core.async "0.2.385"]
   [named-re "1.0.0"]

   ;; boot
   [boot-environ "1.0.3" :scope "test"]
   [adzerk/bootlaces "0.1.13" :scope "test"]])


(require
 '[environ.boot :refer [environ]]
 '[adzerk.bootlaces :refer :all]
 '[system.boot :refer [system]]
 '[jjamppong.systems :refer [dev-system]])


(def +version+ "0.0.1-SNAPSHOT")
(bootlaces! +version+)


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
   (target)))
