(ns re-integrant-app.utils
  (:require [integrant.core :as ig]
            #?(:clj [clojure.data.json :as json])
            #?(:clj [clojure.java.io :as io])))

(defmacro read-config [resource]
  #?(:clj (ig/read-string
           {:readers {'json #(-> %
                                 slurp
                                 (json/read-str :key-fn keyword))}}
           (slurp (io/resource resource)))))

(defmulti reg-sub identity)
(defmulti reg-event identity)
