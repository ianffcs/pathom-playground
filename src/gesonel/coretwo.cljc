(ns gesonel.coretwo
  (:require
   [com.wsscode.pathom.core :as p]
   [com.wsscode.pathom.connect :as pc]
   [clojure.core.async :refer [<!!]]
   [cheshire.core :as json]
   [clojure.edn :as  edn]
   [clojure.string :as str]))

(pc/defresolver my-ip [env _]
  {::pc/output [:user/ip]}
  {:user/ip (-> "https://ipaddr.site"
                slurp
                str/trim)})

(pc/defresolver ip->lat-long
  [env {:user/keys [ip]}]
  {::pc/input  #{:user/ip}
   ::pc/output {:user/ip
                [:user/latitude :user/longitude]}}
  (-> (slurp (str "https://get.geojs.io/v1/ip/geo/" ip ".json"))
      (json/parse-string keyword)
      (select-keys [:latitude :longitude])))

(pc/defresolver person-resolver [env {:keys [person/id] :as params}]
  {::pc/input  #{:person/id}
   ::pc/output [:person/name {:person/address [:address/id]}]}
  {:person/name    "Tom"
   :person/address {:address/id 1}})

(pc/defresolver address-resolver [env {:keys [address/id] :as params}]
  {::pc/input  #{:address/id}
   ::pc/output [:address/city :address/state]}
  {:address/city  "Salem"
   :address/state "MA"})

(def parser
  (p/parser
   {::p/env     {::p/reader               [p/map-reader
                                           pc/reader2
                                           pc/open-ident-reader
                                           p/env-placeholder-reader]
                 ::p/placeholder-prefixes #{">"}}
    ::p/mutate  pc/mutate
    ::p/plugins [(pc/connect-plugin)
                 p/error-handler-plugin
                 p/trace-plugin]}))

(def indexes
  (pc/register {} [my-ip
                   ip->lat-long
                   person-resolver
                   address-resolver]))

(let [parser             (p/parser
                          {::p/env     {::p/reader               [p/map-reader
                                                                  pc/reader2
                                                                  pc/open-ident-reader
                                                                  p/env-placeholder-reader]
                                        ::p/placeholder-prefixes #{">"}}
                           ::p/mutate  pc/mutate
                           ::p/plugins [(pc/connect-plugin)
                                        p/error-handler-plugin
                                        p/trace-plugin]})
      my-ip              (pc/resolver
                          `my-ip
                          {::pc/output [:user/ip]}
                          (fn [_ _]
                            {:user/ip (-> "https://ipaddr.site"
                                          slurp
                                          str/trim)}))
      ip->lat-long       (pc/resolver
                          `ip->lat-long
                          {::pc/input  #{:user/ip}
                           ::pc/output [:user/latitude
                                        :user/longitude]}
                          (fn [_ {:user/keys [ip]}]
                            (let [{:keys [latitude
                                          longitude]} (-> "https://get.geojs.io/v1/ip/geo/%s.json"
                                                          (format ip)
                                                          slurp
                                                          (json/parse-string keyword))]
                              {:user/latitude  (edn/read-string latitude)
                               :user/longitude (edn/read-string longitude)})))
      latlong->woeid     (pc/resolver
                          `latlong->woeid
                          {::pc/input  #{:user/latitude
                                         :user/longitude}
                           ::pc/output [:user/woeid]}
                          (fn [_ {:user/keys [latitude
                                             longitude]}]
                            (let [{:keys [woeid]} (->> (-> "https://www.metaweather.com/api/location/search/?lattlong=%s,%s"
                                                           (format latitude longitude)
                                                           slurp
                                                           (json/parse-string keyword))
                                                       (sort-by :distance)
                                                       first)]
                              {:user/woeid woeid})))
      woeid->temperature (pc/resolver
                          `woeid->temperature
                          {::pc/input  #{:user/woeid}
                           ::pc/output [:user/temperature
                                        :user/sun-set
                                        :user/sun-rise]}
                          (fn [_ {:user/keys [woeid]}]
                            (let [{:keys [consolidated_weather
                                          sun_set
                                          sun_rise]} (->  (str "https://www.metaweather.com/api/location/" woeid)
                                                          slurp
                                                          (json/parse-string keyword))
                                  c_w                (->> consolidated_weather
                                                          (map :the_temp)
                                                          ((fn [coll] (/ (apply + coll)
                                                                        (count coll)))))]
                              {:user/temperature c_w
                               :user/sun-set     sun_set
                               :user/sun-rise    sun_rise})))
      is-cold?           (pc/resolver `is-cold?
                                      {::pc/input  #{:user/temperature}
                                       ::pc/output [:user/cold?]}
                                      (fn [_ {:user/keys [temperature]}]
                                        {:user/cold? (> 20 temperature)}))
      indexes            (pc/register {} [my-ip
                                          ip->lat-long
                                          latlong->woeid
                                          woeid->temperature
                                          is-cold?])]
  (parser {::pc/indexes indexes}
          [:user/ip
           :user/latitude
           :user/longitude
           :user/temperature
           :user/woeid
           :user/cold?]))

#_(p/map-select  {:user/ip        "192.168.15.1"
                  :user/latitude  42
                  :user/longitude 55}
                 [:user/ip
                  :user/latitude
                  :user/longitude])
#_(parser {} [{[:person/id 1]
               [:person/name
                {:person/address
                 [:address/city]}]}])
