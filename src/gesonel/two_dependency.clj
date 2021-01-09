(ns gesonel.two-dependency
  (:require
   [com.wsscode.pathom.core :as p]
   [com.wsscode.pathom.connect :as pc]
   [clojure.core.async :refer [<!!]]
   [jsonista.core :as json]
   [clojure.edn :as  edn]
   [clojure.string :as str]
   [reitit.coercion.malli :as rcm]
   [reitit.dev.pretty :as pretty]
   [reitit.interceptor.sieppari :as sieppari]
   [reitit.http :as http]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as rrc]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [muuntaja.interceptor]
   [reitit.ring.middleware.parameters :as parameters]
   [ring.adapter.jetty :as jetty]
   [muuntaja.core :as m]))

(defn interceptor [number]
  {:enter (fn [ctx] (update-in ctx [:request :number] (fnil + 0) number))})

(def app
  (http/ring-handler
   (http/router
    [["/swagger.json"
      {:get {:no-doc true
             :swagger {:info {:title "my-api"}
                       :basePath "/"}
             :handler (swagger/create-swagger-handler)}}]
     "/api" ["/number" {:interceptors [(interceptor 10)]
                        :get          {:interceptors [(interceptor 100)]
                                       :handler      (fn [req]
                                                       {:status 200
                                                        :body   (select-keys req [:number])})}}]])

   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/api-docs"
      :config {:validatorUrl nil
               :operationsSorter "alpha"}})
    (ring/create-default-handler))

   {:executor reitit.interceptor.sieppari/executor
    :interceptors [swagger/swagger-feature
                   (muuntaja.interceptor/format-interceptor)
                   (muuntaja.interceptor/format-response-interceptor)]}))

(def app2
  (ring/ring-handler
   (ring/router
    [["/api"
      ["/ping" {:get (constantly {:status 200, :body "ping"})}]
      ["/pong" {:post (constantly {:status 200, :body "pong"})}]
      ["/math" {:get  {:parameters {:query [:map
                                            [:x int?]
                                            [:y int?]]}
                       :responses  {200 {:body [:map [:total pos-int?]]}}
                       :handler    (fn [{{{:keys [x y]} :query} :parameters}]
                                     {:status 200
                                      :body   {:total (+ x y)}})}
                :post {:parameters {:body [:map
                                           [:x int?]
                                           [:y int?]]}
                       :responses  {200 {:body [:map [:total pos-int?]]}}
                       :handler    (fn [{{{:keys [x y]} :body} :parameters}]
                                     {:status 200
                                      :body   {:total (+ x y)}})}}]]
     ["/swagger.json"
      {:get {:no-doc true
             :swagger {:info {:title "my-api"}
                       :basePath "/"}
             :handler (swagger/create-swagger-handler)}}]
     ["/lol" {:get {:handler (fn [_]
                               {:status 200
                                :body   "hello"})}}]]
    {:data {:exception  pretty/exception
            :coercion   rcm/coercion
            :muuntaja   m/instance
            :middleware [swagger/swagger-feature
                         parameters/parameters-middleware
                         muuntaja/format-negotiate-middleware
                         muuntaja/format-response-middleware
                         muuntaja/format-request-middleware
                         rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/api-docs"
      :config {:validatorUrl nil
               :operationsSorter "alpha"}}))
   (ring/create-default-handler)))

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

#_((@state :service) {:request-method :get
                      :uri            "/"})

(-main)
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
