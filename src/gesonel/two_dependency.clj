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
  [& _]
  (let [{:keys [service server]} env]
    (swap! state
           (fn [st]
             (some-> st :server .stop)
             (if-not server
               (-> st
                   (merge env)
                   (assoc :server
                          (->> env
                               (jetty/run-jetty service))))
               (some-> st :server .start))))))

#_(-main)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; (pco/defresolver routes [{::keys [operations]}]                                                                 ;;
;;                  {::pco/output [::routes]}                                                                      ;;
;;                  (let [auth [(middlewares/session)                                                              ;;
;;                              (csrf/anti-forgery {:read-token hiete/read-token})                                 ;;
;;                              (body-params/body-params)]                                                         ;;
;;                                                                                                                 ;;
;;                        idx       (pci/register operations)                                                      ;;
;;                        merge-env {:name  ::merge-env                                                            ;;
;;                                   :enter (fn [ctx]                                                               ;;
;;                                            (update ctx :request merge idx))}                                    ;;
;;                        routes    #{["/" :get (conj auth merge-env hiete/render-hiccup ui-home)                  ;;
;;                                     :route-name :conduit.page/home]                                             ;;
;;                                    ["/editor" :get (conj auth merge-env hiete/render-hiccup ui-home)            ;;
;;                                     :route-name :conduit.page/editor]                                           ;;
;;                                    ["/settings" :get (conj auth merge-env hiete/render-hiccup ui-home)          ;;
;;                                     :route-name :conduit.page/settings]                                         ;;
;;                                    ["/register" :get (conj auth merge-env hiete/render-hiccup ui-register)      ;;
;;                                     :route-name :conduit.page/register]                                         ;;
;;                                    ["/login" :get (conj auth merge-env hiete/render-hiccup ui-login)            ;;
;;                                     :route-name :conduit.page/login]                                            ;;
;;                                    ["/article/:slug" :get (conj auth merge-env hiete/render-hiccup ui-home)     ;;
;;                                     :route-name :conduit.page/article]                                          ;;
;;                                    ["/profile/:username" :get (conj auth merge-env hiete/render-hiccup ui-home) ;;
;;                                     :route-name :conduit.page/profile]                                          ;;
;;                                    ["/api/*sym" :post (conj auth merge-env std-mutation)                        ;;
;;                                     :route-name :conduit.api/mutation]}]                                        ;;
;;                    {::routes routes}))                                                                          ;;
;;                                                                                                                 ;;
;; (pco/defresolver service [{::keys [operations]}]                                                                ;;
;;                  {::pco/output [::service]}                                                                     ;;
;;                  (let [routes (fn []                                                                             ;;
;;                                 (-> (pci/register operations)                                                   ;;
;;                                     (p.eql/process [::routes])                                                  ;;
;;                                     ::routes                                                                    ;;
;;                                     route/expand-routes))]                                                      ;;
;;                    {::service (-> {::http/routes routes}                                                        ;;
;;                                   http/default-interceptors                                                     ;;
;;                                   http/dev-interceptors)}))                                                     ;;
;; (defn -main                                                                                                     ;;
;;   [& _]                                                                                                         ;;
;;   (swap! state                                                                                                  ;;
;;          (fn [st]                                                                                                ;;
;;            (some-> st http/stop)                                                                                ;;
;;            (-> (reset! -env (pci/register (operations)))                                                        ;;
;;                (p.eql/process [::service])                                                                      ;;
;;                ::service                                                                                        ;;
;;                (assoc ::http/join? false                                                                        ;;
;;                       ::http/port 8080                                                                          ;;
;;                       ::http/type :jetty)                                                                       ;;
;;                http/create-server                                                                               ;;
;;                http/start))))                                                                                   ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
