(ns gesonel.dev-server
  (:require [io.pedestal.http :as http]
            [hiccup2.core :as h]
            [ring.util.mime-type :as mime]
            [io.pedestal.http.route :as route]
            [lambdaisland.uri :as uri]
            [clojure.string :as string]
            [clojure.edn :as edn])
  (:import (java.nio.charset StandardCharsets)
           (java.net URI)))

(defonce counter (atom 0))

(def utf-8 (str StandardCharsets/UTF_8))

(defn counter-form
  [{:keys [headers query-params]}]
  (let [{:keys [error]} query-params]
    (list
     [:p (str "Olá mundo! " @counter)]
     [:form
      {:action "/add"
       :method "POST"}
      [:input {:name "n"}]
      (when error
        [:pre {:style {:background-color "white"
                       :padding          "1em"
                       :color            "red"}}
         error])
      [:input {:type "submit"}]]
     [:table
      {:style {:padding "1em"}}
      [:tbody
       (for [[k v] headers]
         [:tr
          [:th k]
          [:td [:code (pr-str v)]]])]])))

(defn red
  [req]
  {:html   [:html
            [:head
             [:meta {:charset utf-8}]
             [:title "Ingá"]]
            [:body
             {:style {:background-color "coral"}}
             [:a {:href (URI. (route/url-for ::green))}
              "green"]
             (counter-form req)]]
   :status 200})


(defn green
  [req]
  {:html   [:html
            [:head
             [:meta {:charset utf-8}]
             [:title "Ingá"]]
            [:body
             {:style {:background-color "lightgreen"}}
             [:a {:href (URI. (route/url-for ::red))}
              "red"]
             (counter-form req)]]
   :status 200})

(defn add
  [{:keys [headers body]}]
  (let [v (edn/read-string (second (string/split (slurp body) #"=")))]
    (swap! counter + v)
    {:headers {"Location" (-> headers (get "referer") uri/uri (assoc :query nil) str)}
     :status  303}))

(def ->html
  {:name  ::->html
   :leave (fn [{:keys [response route]
               :as   ctx}]
            (if (contains? response :html)
              (let [{:keys [route-name]} route]
                (do #_#_binding [hiccup.util/*base-url* "abc/123"]
                    (-> response
                        (dissoc :html)
                        (assoc :body (str "<!DOCTYPE html>\n"
                                          (h/html {:mode :html} (:html response))))
                        (assoc-in [:headers "Content-Type"] (mime/default-mime-types "html"))
                        (->> (assoc ctx :response)))))
              ctx))})

(def on-error
  {:name  ::on-error
   :error (fn [{:keys [request]
               :as   ctx} ex]
            (let [referer  (-> request :headers (get "referer") uri/uri)
                  response {:headers {"Location" (str (update referer :query
                                                              (fn [q]
                                                                (-> q
                                                                    uri/query-string->map
                                                                    (assoc :error (ex-message ex))
                                                                    uri/map->query-string))))}
                            :status  303}]
              (-> ctx
                  (assoc :response response))))})

(def routes
  `#{["/green" :get [on-error ->html green]]
     ["/red" :get [on-error ->html red]]
     ["/add" :post [on-error ->html add]]})

(defonce state (atom nil))

(defn -main
  [& _]
  (swap! state (fn [st]
                 (some-> st http/stop)
                 (-> {::http/port   8080
                      ::http/join?  false
                      ::http/type   :jetty
                      ::http/routes #(route/expand-routes routes)}
                     http/default-interceptors
                     http/dev-interceptors
                     http/create-server
                     http/start))))
