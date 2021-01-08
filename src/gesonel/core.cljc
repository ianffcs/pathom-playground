(ns gesonel.core
  (:require
   [clojure.java.io :as io]
   [cheshire.core :as json]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.smart-map :as psm]
   [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
   [com.wsscode.pathom3.format.eql :as pf.eql]
   [com.wsscode.pathom3.interface.eql :as p.eql]
   [edn-query-language.core :as eql]
   [clojure.string :as str]
   [com.wsscode.pathom3.connect.runner :as pcr]
   [com.wsscode.pathom3.entity-tree :as p.ent]
   [com.wsscode.pathom3.connect.planner :as pcp]
   [com.wsscode.pathom3.format.shape-descriptor :as pfsd]
   [io.pedestal.http :as http]))

(pco/defresolver C->K [{:keys [temperature]}]
  {:temperature-kelvin (+ temperature 273.15)})

(pco/defresolver my-ip
  []
  {:ip (-> "https://ipaddr.site"
           slurp
           str/trim)})

(pco/defresolver ip->lat-long
  [{:keys [ip]}]
  {::pco/output [:latitude :longitude]}
  (-> (slurp (str "https://get.geojs.io/v1/ip/geo/" ip ".json"))
      (json/parse-string keyword)
      (select-keys [:latitude :longitude])))

(pco/defresolver latlong->woeid
  [{:keys [latitude longitude]}]
  {:woeid
   (-> (slurp
        (str "https://www.metaweather.com/api/location/search/?lattlong="
             latitude "," longitude))
       (json/parse-string keyword)
       first
       :woeid)})

(pco/defresolver woeid->temperature
  [{:keys [woeid]}]
  {:temperature
   (-> (slurp (str "https://www.metaweather.com/api/location/" woeid))
       (json/parse-string keyword)
       :consolidated_weather
       first
       :the_temp)})

(pco/defresolver query->meta-api-resp
  [{:keys [query]}]
  {::pco/output [{:meta-api-resp [:title
                                  :location_type
                                  :latt_long
                                  :woeid
                                  :distance]}]}
  {:meta-api-resp
   (-> (slurp (str "https://www.metaweather.com/api/location/search/?query=" query))
       (json/parse-string keyword))})

;; define a map for indexed access to user data
(def users-db
  {1 #:acme.user{:name     "Ian"
                 :surname  "Fernandez"
                 :email    "user@provider.com"
                 :birthday "1989-10-25"
                 :ip       "191.248.221.148"}
   2 #:acme.user{:name     "Bruno"
                 :surname  "Rodrigues"
                 :email    "bruno@provider.com"
                 :birthday "1975-09-11"
                 :ip       "201.43.195.67"}})

(pco/defmutation add-user [user]
  (assoc users-db
         (inc (last (keys users-db)))
         user))

;; pull stored user info from id
(pco/defresolver user-by-id [{:keys [acme.user/id]}]
  {::pco/output
   [:acme.user/name
    :acme.user/email
    :acme.user/birthday]}
  (get users-db id))

;; extract birth year from birthday
(pco/defresolver birth-year [{:keys [acme.user/birthday]}]
  {:acme.user/birth-year (first (str/split birthday #"-"))})

(pco/defresolver ip->acme-user  [{:acme.user/keys [ip]}]
  {::pco/output [:acme.user/id
                 :acme.user/name
                 :acme.user/email
                 :acme.user/birthday]}
  (->> users-db
       (keep (fn [[id v]]
               (when (= ip (get v :acme.user/ip))
                 (assoc v :acme.user/id id))))
       first))

(pco/defresolver fullname
  [{:acme/keys [name surname]}]
  {:acme/full-name (str name " " surname)})

(pco/defmutation save-file [{::keys [file-path file-content] :as file}]
  (spit file-path file-content)
  file)

(pco/defresolver file-size [{::keys [file-path]}]
  {::file-size (.length (io/file file-path))})

#_(p.eql/process env
                 [`(save-file {::file-path "./file.txt" ::file-content "contents here"})])
#_(p.eql/process env
                 [{`(save-file {::file-path "./file.txt" ::file-content "contents here"})
                   [::file-path
                    ::file-size]}])
(def env
  (pci/register [query->meta-api-resp
                 my-ip
                 ip->lat-long
                 latlong->woeid
                 woeid->temperature
                 (pbir/single-attr-resolver :temperature :cold? (partial < 20 ))
                 (pbir/single-attr-resolver :temperature :temperature-kelvin (partial + 273.15))
                 (pbir/single-attr-resolver :temperature :temperature-fahrenheit #(-> %
                                                                                      (* 9.0)
                                                                                      (/ 5.0)
                                                                                      (+ 32)))
                 user-by-id
                 birth-year
                 fullname
                 add-user
                 (pbir/equivalence-resolver
                  :acme.user/ip
                  :ip)
                 ip->acme-user
                 (pbir/attribute-map-resolver :song/id :song/name
                                              {1 "Marchinha Psicotica de Dr. Soup"
                                               2 "There's Enough"})


                 (pbir/constantly-resolver ::song-analysis
                                           {1 {:song/duration 280 :song/tempo 98}
                                            2 {:song/duration 150 :song/tempo 130}})

                 (pbir/attribute-table-resolver ::song-analysis :song/id
                                                [:song/duration :song/tempo])
                 save-file
                 file-size]))

;; using helper syntax
#_(pco/resolver `op-name
                {::pco/output [::foo]}
                (fn [env input] {::foo "bar"}))

#_(p.eql/process env
                 [{'(:>/enzzo #:acme.user{:name     "Enzzo"
                                          :surname  "Cavallo"
                                          :email    "souenzzo@provider.com"
                                          :birthday "1975-12-12"
                                          :ip       "201.17.80.191"})
                   [:acme.user/id]}])
#_(p.eql/process env
                 [`(add-user #:acme.user{:name     "Enzzo"
                                         :surname  "Cavallo"
                                         :email    "souenzzo@provider.com"
                                         :birthday "1975-12-12"
                                         :ip       "201.17.80.191"})
                  [:acme.user/id
                   :acme.user/ip
                   :temperature]])
;; using config map
#_(pco/resolver
   {::pco/op-name `op-name
    ::pco/output  [::foo]
    ::pco/resolve (fn [env input] {::foo "bar"})})

#_(:config query->meta-api-resp)
#_(->> (psm/smart-map env {:query "lon"})
       #_(psm/sm-env)
       #_:meta-api-resp)

#_(let [user1         (psm/smart-map env {:acme.user/id 1})
        user1-fetched (psm/sm-touch! user1 [:ip :latitute :longitude])]
    (select-keys user1-fetched [:temperature :temperature-fahrenheit]))


#_(p.eql/process env
                 [{'(:>/bret {:acme/name "Bret" :acme/surname "Victor"})
                   [:acme/full-name
                    {'(:>/bard {:acme/name "Bard"})
                     [:acme/full-name]}]}])
#_((psm/smart-map env {:acme.user/ip "191.248.221.148"}) :acme.user/id)
#_(p.eql/process
   env
   {:acme.user/id 2}
   [:temperature
    :temperature-fahrenheit
    :temperature-kelvin
    :latitude
    :longitude
    :ip
    :woeid
    :acme.user/email
    :acme.user/name
    :acme.user/ip
    :acme.user/id
    :cold?])

#_(p.eql/process
   env
   {:query "janeiro"}
   [{:meta-api-resp
     [:title
      :temperature
      :temperature-fahrenheit]}])

;; bug aqui
#_(p.eql/process env
                 {:acme.user/ip "191.248.221.148"}
                 [:temperature
                  :temperature-fahrenheit
                  :temperature-kelvin
                  :latitude
                  :longitude
                  :ip
                  :woeid
                  :acme.user/email
                  :acme.user/name
                  :acme.user/id
                  :acme.user/ip
                  :cold?])

#_(let [env (pci/register
             [(pbir/constantly-resolver ::pi 3.1415)
              (pbir/single-attr-resolver ::pi ::tau #(* % 2))
              (pbir/constantly-resolver ::pi-worlds
                                        [{::pi 3.14}
                                         {::pi 3.14159}
                                         {::pi 6.8}
                                         {::pi 20}
                                         {::pi 10}])])]
    #_(p.eql/process env {::pi 2.3} [::tau])
    (p.eql/process env [{[::pi 2.3] [::tau]}])
    #_(p.eql/process env [::tau :com.wsscode.pathom3.connect.runner/run-stats]))

(defn meter<->feet-resolver
  [attribute]
  (let [foot-kw  (keyword (namespace attribute) (str (name attribute) "-ft"))
        meter-kw (keyword (namespace attribute) (str (name attribute) "-m"))]
    [(pbir/single-attr-resolver meter-kw foot-kw #(* % 3.281))
     (pbir/single-attr-resolver foot-kw meter-kw #(/ % 3.281))
     (pbir/constantly-resolver :math/PI 3.1415)]))

#_(let [sm (psm/smart-map (pci/register (meter<->feet-resolver :foo)))]
    [(-> sm (assoc :foo-m 169) :foo-ft)
     (-> sm (assoc :foo-ft 358) :foo-m)])

#_(let [mock-todos-db  [{::todo-message "Write demo on params"
                         ::todo-done?   true}
                        {::todo-message "Pathom in Rust"
                         ::todo-done?   false}]
        filter-matches (fn [match coll]
                         (let [match-keys (keys match)]
                           (if (seq match)
                             (filter
                              #(= match
                                  (select-keys % match-keys))
                              coll)
                             coll)))
        todos-resolver (pco/resolver `todos-resolver
                                     {::pco/output
                                      [{::todos
                                        [::todo-message
                                         ::todo-done?]}]}
                                     (fn [env _]
                                       {::todos
                                        (filter-matches
                                         (pco/params env)
                                         mock-todos-db)}))
        env            (pci/register [todos-resolver])
        sm             (psm/smart-map env {})]
    (psm/sm-touch! sm ['(::todos {::todo-done? false})])
    (p.eql/process env ['(::todos {::todo-done? false})]))

#_(let [error-mut (pco/mutation
                   `error-mut {}
                   (fn [_env params]
                     (throw (ex-info "Error" {:data true}))))
        reg       (pci/register error-mut)]
    (p.eql/process reg
                   [`(error-mut)]))


(defn main [args]
  ;; start smart maps with call args
  (let [sm (psm/smart-map env args)]
    (println (str "It's currently "
                  (:temperature sm)
                  "C at " (pr-str args)))))
(require '[clojure.java.io :as io]
         '[cheshire.core :as json]
         '[com.wsscode.pathom3.connect.indexes :as pci]
         '[com.wsscode.pathom3.connect.operation :as pco]
         '[com.wsscode.pathom3.interface.eql :as p.eql]
         '[io.pedestal.http :as http]
         '[io.pedestal.test :as pedestal.test])
(let [env     (-> (pci/register
                   [(pco/resolver `my-ip
                                  {::pco/input  []
                                   ::pco/output [:ip]}
                                  (fn [_ _]
                                    {:ip (-> "https://ipaddr.site"
                                             slurp
                                             str/trim)}))
                    (pco/resolver `ip->lat-long
                                  {::pco/input  [:ip]
                                   ::pco/output [:latitude :longitude]}
                                  (fn [_ {:keys [ip]}]
                                    (-> (slurp (str "https://get.geojs.io/v1/ip/geo/" ip ".json"))
                                        (json/parse-string keyword)
                                        (select-keys [:latitude :longitude]))))
                    (pco/resolver `latlong->woeid
                                  {::pco/input  [:latitude :longitude]
                                   ::pco/output [:woeid]}
                                  (fn [_ {:keys [latitude longitude]}]
                                    {:woeid
                                     (-> (slurp
                                          (str "https://www.metaweather.com/api/location/search/?lattlong="
                                               latitude "," longitude))
                                         (json/parse-string keyword)
                                         first
                                         :woeid)}))
                    (pco/resolver `woeid->temperature
                                  {::pco/input  [:woeid]
                                   ::pco/output [:temperature]}
                                  (fn [_  {:keys [woeid]}]
                                    {:temperature
                                     (-> (slurp (str "https://www.metaweather.com/api/location/" woeid))
                                         (json/parse-string keyword)
                                         :consolidated_weather
                                         first
                                         :the_temp)}))])
                  (merge {::http/type  :jetty
                          ::http/port  3000
                          ::http/join? false}))
      routes  #{["/ip2temp"
                 :post
                 (partial (fn [env req]
                            {:status  200
                             :body    (json/encode
                                       (p.eql/process env
                                                      (-> req
                                                          :body
                                                          io/reader
                                                          (json/parse-stream keyword))
                                                      [:ip
                                                       :latitude
                                                       :longitude
                                                       :woeid
                                                       :temperature]))
                             :headers {"Content-Type" "application/json"}}) env)
                 :route-name
                 ::index]}
      system  (-> env
                  (assoc ::http/routes routes)
                  http/default-interceptors
                  http/dev-interceptors
                  http/create-server)
      service (::http/service-fn system)]
  (pedestal.test/response-for
   service
   :post "/ip2temp"
   :headers {"Content-Type" "application/json"}
   :body
   (json/encode {})))
