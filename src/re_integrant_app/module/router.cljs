(ns re-integrant-app.module.router
  (:require [integrant.core :as ig]
            [re-frame.core :as re-frame]
            [day8.re-frame.tracing :refer-macros [fn-traced]]
            [bidi.bidi :as bidi]
            [pushy.core :as pushy]))

(defn- setup-router [routes]
  (letfn [(dispatch-route [{:keys [:handler :route-params]}]
            (let [panel-name (keyword (str (name handler) "-panel"))]
              (re-frame/dispatch [::set-active-panel panel-name route-params])))
          (parse-url [url]
            (when (empty? url)
              (set! js/window.location (str js/location.pathname "#/")))
            (let [url (-> url
                          (clojure.string/split #"&")
                          (first))]
              (bidi/match-route routes url)))]
    (let [history (pushy/pushy dispatch-route parse-url)]
      (.setUseFragment (aget history "history") true)
      (pushy/start! history)
      {:history history :routes routes})))

(defn- go-to-page [{:keys [history routes]} route]
  (pushy/set-token! history (apply bidi/path-for (cons routes route))))

;; Initial DB
(def initial-db {::active-panel :none ::router nil})

;; Subscriptions
(defmulti reg-sub identity)
(defmethod reg-sub ::active-panel [k]
  (re-frame/reg-sub k #(::active-panel %)))
(defmethod reg-sub ::route-params [k]
  (re-frame/reg-sub k #(::route-params %)))

;; Events
(defmulti reg-event identity)
(defmethod reg-event ::init [k]
  (re-frame/reg-event-db
   k [re-frame/trim-v]
   (fn-traced
    [db [router]]
    (-> db
        (merge initial-db)
        (assoc ::router router)))))
(defmethod reg-event ::halt [k]
  (re-frame/reg-event-db
   k [re-frame/trim-v]
   (fn-traced
    [db _]
    (->> db
         (filter #(not= (namespace (key %)) (namespace ::x)))
         (into {})))))
(defmethod reg-event ::go-to-page [k]
  (re-frame/reg-event-db
   k [re-frame/trim-v]
   (fn-traced [db [route]]
              (let [{:keys [::router]} db]
                (go-to-page router route))
              db)))
(defmethod reg-event ::set-active-panel [k]
  (re-frame/reg-event-db
   k [re-frame/trim-v]
   (fn-traced [db [panel-name route-params]]
              (assoc db
                     ::active-panel panel-name
                     ::route-params route-params))))

(defmethod ig/init-key :re-integrant-app.module/router
  [k routes]
  (js/console.log (str "Initializing " k))
  (let [subs (->> reg-sub methods (map key))
        events (->> reg-event methods (map key))
        router (setup-router routes)]
    (->> subs (map reg-sub) doall)
    (->> events (map reg-event) doall)
    (re-frame/dispatch-sync [::init router])
    {:subs subs :events events :router router}))

(defmethod ig/halt-key! :re-integrant-app.module/router
  [k {:keys [:subs :events :router]}]
  (js/console.log (str "Halting " k))
  (let [{:keys [:history]} router]
    (re-frame/dispatch-sync [::halt])
    (->> subs (map re-frame/clear-sub) doall)
    (->> events (map re-frame/clear-event) doall)
    (pushy/stop! history)))
