FROM clojure:tools-deps

WORKDIR /app

COPY deps.edn /app
COPY src /app/src

RUN clj -P

EXPOSE 5000

ENTRYPOINT "clj" "-M" "-m" "app.core"
