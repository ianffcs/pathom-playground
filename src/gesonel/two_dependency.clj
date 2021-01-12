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
            [integrant.core :as ig]))

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
                       :handler    (fn [{{{:keys [x y]} :query} :parameters}]
                                     {:status 200
                                      :body   {:total (+ x y)}})}
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

(def config
  {::db {:dbtype "hsqldb"
         :dbname "example"}
   ::http {:handler (ig/ref ::db)
           :port 3000
           :join? false
           :service #'app}})

(defmethod ig/init-key ::db
  [_ env]
  (let [ds (jdbc/get-datasource env)]
    (assoc env
           :datasource ds
           :connection (jdbc/get-connection ds))))

(defmethod ig/halt-key! ::db
  [_ env]
  (some-> env :db/connection .close))

(defmethod ig/init-key ::http
  [_ {:keys [service] :as env}]
  (assoc env
         :http/server (jetty/run-jetty service env)))

(defmethod ig/halt-key! ::http
  [_ env]
  (some-> env :http/server .stop))

(defonce state (atom nil))

(defn stop! [state]
  (swap! state
         (fn [{::keys [system] :as env}]
           (when system
             (assoc env ::system (ig/halt! system))))))

(defn -main
  [& _]
  (let [system (ig/init config)]
    (reset! state (assoc config ::system system))))
#_(def create-address-table
    "create table address (
  id int auto_increment primary key,
  name varchar(32),
  email varchar(255))")
#_(->  {:request-method :get
        :uri            "/api/math"
        #_#_:query-params {:x "1"
                           :y "2"}}
       ((@state :http/service))
       #_(update :body (comp #(json/parse-string % true) slurp)))

(comment
  (stop! state)
  (-main))
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
