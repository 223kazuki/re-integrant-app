(ns cljs.user
  (:require [re-integrant-app.core :refer [system config start stop]]
            [meta-merge.core :refer [meta-merge]])
  (:require-macros [re-integrant-app.utils :refer [read-config]]))

(enable-console-print!)

(println "dev mode")

(swap! config #(meta-merge % (read-config "dev.edn")))

(defn reset []
  (stop)
  (start))

(defn ^:export init []
  (start))
