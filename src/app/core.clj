(ns app.core (:gen-class) (:require [io.pedestal.http :as http]
                                    [environ.core :refer [env]]
                                    [app.jwt :refer [decode-jwt]]))

(def jwk-endpoint "https://vncz.us.auth0.com/.well-known/jwks.json")

(def heroes [{:name "Clark" :surname "Kent" :hero "Superman"}
             {:name "Bruce" :surname "Wayne" :hero "Batman"}
             {:name "James" :surname "Logan" :hero "Wolverine"}
             {:name "Steve" :surname "Rogers" :hero "Captain America"}
             {:name "Bruce" :surname "Banner" :hero "Hulk"}])

(defn hello-world [req] {:status 200 :body (:claims req)})

(defn get-hero [{{:keys [hero]} :path-params
                 {:keys [extended]} :query-params}]
  (if-let [hero (->> heroes
                     (filter #(= hero (:hero %)))
                     first)]
    {:status 200 :body (if extended hero (dissoc hero :hero))}
    {:status 404}))

(defn get-claims [req] {:status 200 :body (:claims req)})

(defn get-heroes [_] {:status 200 :body heroes})

(def routes #{["/heroes/:hero" :get get-hero :route-name :get-hero]
              ["/heroes" :get get-heroes :route-name :get-heroes]
              ["/claims" :get get-claims :route-name :get-claims]
              ["/" :get hello-world :route-name :hello-world]})

(def service-map (-> {::http/routes routes
                      ::http/type   :immutant
                      ::http/host   "0.0.0.0"
                      ::http/join?  false
                      ::http/port   (Integer. (or (env :port) 5000))}
                 http/default-interceptors
                 (update ::http/interceptors into [http/json-body
                                                   (decode-jwt {:required? true
                                                                :jwk-endpoint jwk-endpoint})])))

(defn -main []
  (http/start (http/create-server service-map)))

;; For interactive development
(defonce server (atom nil))

(defn start-dev []
  (reset! server
          (http/start (http/create-server
                       (assoc service-map
                              ::http/join? false)))))

(defn stop-dev []
  (when-let [s @server] (http/stop s)))

(defn restart []
  (stop-dev)
  (start-dev))
(restart)
