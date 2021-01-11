(ns gesonel.two-dependency
  (:require [muuntaja.core :as m]
            [muuntaja.interceptor :as minterceptor]
            [reitit.coercion.malli :as rcm]
            [reitit.dev.pretty :as pretty]
            [reitit.http :as http]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.adapter.jetty :as jetty]
            [next.jdbc :as jdbc]
            [honeysql.core :as sql]
            [cheshire.core :as json]))
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
     ["/api" {:interceptors [(interceptor 42)]}
      ["/number" {:interceptors [(interceptor 10)]
                  :get          {:interceptors [(interceptor 100)]
                                 :handler      (fn [req]
                                                 {:status 200
                                                  :body   (select-keys req [:number])})}}]]])

   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/api-docs"
      :config {:validatorUrl nil
               :operationsSorter "alpha"}})
    (ring/create-default-handler))

   {:executor reitit.interceptor.sieppari/executor
    :interceptors [swagger/swagger-feature
                   (minterceptor/format-interceptor)
                   (minterceptor/format-response-interceptor)]}))

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
(defn restart-http [{:keys [service server]} st]
  (some-> st :server .stop)
  (if-not server
    (-> st
        (merge env)
        (assoc :server
               (->> env
                    (jetty/run-jetty service))))
    (some-> st :server .start)))

(defonce state (atom nil))

(defn -main
  [& _]
  (swap! state (partial restart-http env)))

#_((@state :service) {:request-method :get
                      :uri            "/"})
#_(->  {:request-method :get
        :uri            "/api/number"}
       ((@state :service))
       (update :body (comp #(json/parse-string % true) slurp)))

(-main)
