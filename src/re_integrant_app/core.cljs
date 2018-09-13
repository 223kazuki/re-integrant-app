(ns re-integrant-app.core
  (:require [integrant.core :as ig]
            [re-integrant-app.module.app]
            [re-integrant-app.module.router]
            [re-integrant-app.module.moment])
  (:require-macros [re-integrant-app.utils :refer [read-config]]))

(defonce system (atom nil))

(def config (atom (read-config "config.edn")))

(defn start []
  (reset! system (ig/init @config)))

(defn stop []
  (when @system
    (ig/halt! @system)
    (reset! system nil)))

(defn ^:export init []
  (start))
