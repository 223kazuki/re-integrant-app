(ns re-integrant-app.module.moment
  (:require [integrant.core :as ig]
            [re-frame.core :as re-frame]
            [day8.re-frame.tracing :refer-macros [fn-traced]]
            [cljsjs.moment]
            [clojure.core.async :refer [chan go go-loop >! <! timeout close!]]))

(defn- create-loop [f tick]
  (let [timing? (chan)
        kick #(do (f)
                  (go
                    (<! (timeout tick))
                    (>! timing? true)))]
    (go-loop []
      (when (<! timing?)
        (kick)
        (recur)))
    (kick)
    #(close! timing?)))

;; Initial DB
(def initial-db {::now nil})

;; Subscriptions
(defmulti reg-sub identity)
(defmethod reg-sub ::now [k]
  (re-frame/reg-sub-raw
   k (fn [app-db _]
       (let [close (create-loop #(re-frame/dispatch [::update-now]) 1000)]
         (reagent.ratom/make-reaction
          #(get-in @app-db [::now])
          :on-dispose close)))))

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
        events (->> reg-event methods (map key))]
    (->> subs (map reg-sub) doall)
    (->> events (map reg-event) doall)
    (re-frame/dispatch-sync [::init])
    {:subs subs :events events}))

;; Halt
(defmethod ig/halt-key! :re-integrant-app.module/moment
  [k {:keys [:subs :events]}]
  (js/console.log (str "Halting " k))
  (re-frame/dispatch-sync [::halt])
  (->> subs (map re-frame/clear-sub) doall)
  (->> events (map re-frame/clear-event) doall))
