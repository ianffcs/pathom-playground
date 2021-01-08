(ns gesonel.two-dependency
  (:require
   [com.wsscode.pathom.core :as p]
   [com.wsscode.pathom.connect :as pc]
   [clojure.core.async :refer [<!!]]
   [jsonista.core :as json]
   [clojure.edn :as  edn]
   [clojure.string :as str]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as rrc]
   [reitit.coercion.malli :as rcm]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [ring.adapter.jetty :as jetty]))

(def app
  (ring/ring-handler
   (ring/router
    ["/api"
     #_["/math" {:get {:parameters {:query {:x int?, :y int?}}
                       :responses  {200 {:body {:total pos-int?}}}
                       :handler    (fn [{{{:keys [x y]} :query} :parameters}]
                                     {:status 200
                                      :body   {:total (+ x y)}})}}]]
    ;; router data affecting all routes
    {:data {:coercion   rcm/coercion
            :middleware [rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]}})))


(def env
  {:port    3000
   :join?   false
   :service #'app
   :server  nil})

(defonce state (atom nil))
(defn -main
  [{:keys [service server]
    :as   env} & _]
  (swap! state
         (fn [st]
           (some-> st :server .stop)
           (if-not server
             (-> st
                 (merge env)
                 (assoc :server
                        (->> env
                             (jetty/run-jetty service))))
             (some-> st :server .start)))))

#_(-main env)
