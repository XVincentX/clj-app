(ns app.auth
  (:require [cheshire.core :as json]
            [io.pedestal.interceptor.helpers :as interceptor])
  (:import (java.net URL)
           (com.auth0.jwt JWT)
           (com.auth0.jwt.exceptions SignatureVerificationException AlgorithmMismatchException TokenExpiredException JWTVerificationException JWTDecodeException)
           (com.auth0.jwt.algorithms Algorithm)
           (com.auth0.jwk UrlJwkProvider)
           (com.auth0.jwt.interfaces RSAKeyProvider)
           (org.apache.commons.codec Charsets)
           (org.apache.commons.codec.binary Base64)))

(defn- new-jwk-provider
  [url]
  (-> (URL. url)
      UrlJwkProvider.))

(def ^{:private true} rsa-key-provider
  (memoize
    (fn [url]
      (let [jwk-provider (new-jwk-provider url)]
        (reify RSAKeyProvider
          (getPublicKeyById [_ key-id]
            (-> (.get jwk-provider key-id)
                (.getPublicKey)))
          (getPrivateKey [_] nil)
          (getPrivateKeyId [_] nil))))))

(defn- base64->map
  [base64-str]
  (-> base64-str
      Base64/decodeBase64
      (String. Charsets/UTF_8)
      json/parse-string))

(defn- decode-token
  [algorithm token {:keys [issuer leeway-seconds]}]
  (let [add-issuer #(if issuer
                      (.withIssuer % (into-array String [issuer]))
                      %)]
    (-> algorithm
        (JWT/require)
        (.acceptLeeway (or leeway-seconds 0))
        add-issuer
        .build
        (.verify token)
        .getPayload
        base64->map)))

(defn- decode [token {:keys [jwk-endpoint] :as opts}]
  (-> jwk-endpoint
      rsa-key-provider
      Algorithm/RSA256
      (decode-token token opts)))

(defn- find-token [{:keys [headers]}]
  (some->> headers
           (filter #(.equalsIgnoreCase "authorization" (key %)))
           first
           val
           (re-find #"(?i)^Bearer (.+)$")
           last))

(defn- unauthorized [text]
  {:status  401
   :headers {}
   :body    text})

(defn decode-jwt [{:keys [required?] :as opts}]
  (interceptor/before
    ::decode-jwt
    (fn [ctx]
      (try
        (if-let [token (find-token (:request ctx))]
          (->> (decode token opts)
               (assoc ctx :claims))
          (if required? (assoc ctx :response (unauthorized "Token not provided"))
                        (assoc ctx :claims {})))

        (catch JWTDecodeException _
          (assoc ctx :response (unauthorized "The token provided is not valid")))

        (catch SignatureVerificationException _
          (assoc ctx :response (unauthorized "Signature could not be verified")))

        (catch AlgorithmMismatchException _
          (assoc ctx :response (unauthorized "Algorithm verification problem")))

        (catch TokenExpiredException _
          (assoc ctx :response (unauthorized "Token has expired")))

        (catch JWTVerificationException _
          (assoc ctx :response (unauthorized "Invalid claims")))))))
