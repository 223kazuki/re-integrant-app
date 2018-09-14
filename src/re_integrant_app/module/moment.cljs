(ns re-integrant-app.module.moment
  (:require [integrant.core :as ig]
            [re-frame.core :as re-frame]
            [day8.re-frame.tracing :refer-macros [fn-traced]]
            [cljsjs.moment]
            [clojure.core.async :refer [chan go go-loop >! <! timeout close!]]))

;; Initial DB
(def initial-db {::now nil})

;; Subscriptions
(defmulti reg-sub identity)
(defmethod reg-sub ::now [k]
  (re-frame/reg-sub k #(::now %)))

;; Events
(defmulti reg-event identity)
(defmethod reg-event ::init [k]
  (re-frame/reg-event-db
   k [re-frame/trim-v]
   (fn-traced
    [db _]
    (-> db
        (merge initial-db)
        (assoc ::now (js/moment))))))
(defmethod reg-event ::halt [k]
  (re-frame/reg-event-db
   k [re-frame/trim-v]
   (fn-traced
    [db _]
    (->> db
         (filter #(not= (namespace (key %)) (namespace ::x)))
         (into {})))))
(defmethod reg-event ::update-now [k]
  (re-frame/reg-event-db
   k [re-frame/trim-v]
   (fn-traced
    [db _]
    (assoc db ::now (js/moment)))))

;; Init
(defmethod ig/init-key :re-integrant-app.module/moment
  [k _]
  (js/console.log (str "Initializing " k))
  (let [subs (->> reg-sub methods (map key))
        events (->> reg-event methods (map key))
        timing (chan)
        kick #(go
                (<! (timeout 1000))
                (>! timing true))]
    (->> subs (map reg-sub) doall)
    (->> events (map reg-event) doall)
    (re-frame/dispatch-sync [::init])
    (go-loop []
      (when (<! timing)
        (go (re-frame/dispatch [::update-now]))
        (kick)
        (recur)))
    (kick)
    {:subs subs :events events :closer #(close! timing)}))

;; Halt
(defmethod ig/halt-key! :re-integrant-app.module/moment
  [k {:keys [:subs :events :closer]}]
  (js/console.log (str "Halting " k))
  (closer)
  (re-frame/dispatch-sync [::halt])
  (->> subs (map re-frame/clear-sub) doall)
  (->> events (map re-frame/clear-event) doall))
