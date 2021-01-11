(ns gesonel.two-dependency
  (:require [muuntaja.core :as m]
            [muuntaja.interceptor :as minterceptor]
            [reitit.coercion.malli :as rcm]
            [reitit.dev.pretty :as pretty]
            [reitit.http :as http]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.ring :as ring]
            [reitit.http.coercion :as rhc]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.exception :as exception] ;; interceptor mode in reitit is called http
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.adapter.jetty :as jetty]
            [next.jdbc :as jdbc]
            [honeysql.core :as sql]
            [cheshire.core :as json]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]))
(defn interceptor-n [number]
  {:enter (fn [ctx]
            (update-in ctx [:request :number] (fnil + 0) number))})

(defn interceptor [f x]
  {:enter (fn [ctx] (f (update-in ctx [:request :via] (fnil conj []) {:enter x})))
   :leave (fn [ctx] (f (update-in ctx [:response :body] conj {:leave x})))})

(def app
  (http/ring-handler
   (http/router
    [["/swagger.json"
      {:get {:no-doc  true
             :swagger {:info     {:title "my-api"}
                       :basePath "/"}
             :handler (swagger/create-swagger-handler)}}]
     ["/api" {:interceptors [(interceptor-n 42)]}
      ["/ping" {:get {:handler (constantly {:status 200, :body "ping"})}}]
      ["/pong" {:post {:handler (constantly {:status 200, :body "pong"})}}]
      ["/math" {:get  {:parameters {:query [:map
                                            [:x int?]
                                            [:y int?]]}
                       :responses  {200 {:body [:map [:total pos-int?]]}}
                       :handler    (fn [{{{:keys [x y]} :query} :parameters :as req}]
                                     (clojure.pprint/pprint req)
                                     {:status 200
                                      :body   {:total x}})}
                :post {:parameters {:body [:map
                                           [:x int?]
                                           [:y int?]]}
                       :responses  {200 {:body [:map [:total pos-int?]]}}
                       :handler    (fn [{{{:keys [x y]} :body} :parameters}]
                                     {:status 200
                                      :body   {:total (+ x y)}})}}]
      ["/number" {:interceptors [(interceptor-n 10)]
                  :get          {:interceptors [(interceptor-n 100)]
                                 :handler      (fn [req]
                                                 (clojure.pprint/pprint (select-keys req [:number]))
                                                 {:status 200
                                                  :body   (select-keys req [:number])})}}]]]
    {:exception    pretty/exception
     :data {:coercion     rcm/coercion
            :muuntaja     m/instance
            :interceptors [swagger/swagger-feature
                           (parameters/parameters-interceptor)
                           (muuntaja/format-negotiate-interceptor)
                           (muuntaja/format-response-interceptor)
                           (exception/exception-interceptor)
                           (muuntaja/format-request-interceptor)
                           (rhc/coerce-response-interceptor)
                           (rhc/coerce-exceptions-interceptor)
                           (rhc/coerce-request-interceptor)]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path   "/api-docs"
      :config {:validatorUrl     nil
               :operationsSorter "alpha"}})
    (ring/create-default-handler))
   {:executor     reitit.interceptor.sieppari/executor}))


(def env
  {:port    3000
   :join?   false
   :service #'app
   :server  nil
   :dbtype "hsqldb"
   :dbname "example"})

(defn restart-db [env st]
  (some-> st :db/connection .close)
  (some-> st dissoc :db/datasource)
  (let [ds (jdbc/get-datasource env)
        connection (jdbc/get-connection ds)]
    (merge st {:db/datasource ds
               :db/connection connection})))

(defn restart-http [{:keys [service server]} st]
  (some-> st :http/server .stop)
  (if-not server
    (assoc st
           :http/service service
           :http/server
           (->> env
                (jetty/run-jetty service)))
    (some-> st :http/server .start)))

(defonce state (atom nil))

(defn -main
  [& _]
  (swap! state (partial restart-db env))
  (swap! state (partial restart-http env)))

#_((@state :service) {:request-method :get
                      :uri            "/"})
#_(->  {:request-method :get
        :uri            "/api/number"}
       ((@state :service))
       (update :body (comp #(json/parse-string % true) slurp)))

(-main)
