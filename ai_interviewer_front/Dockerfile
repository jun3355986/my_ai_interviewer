FROM ghcr.io/cirruslabs/flutter:stable AS builder

WORKDIR /app
COPY pubspec.yaml pubspec.lock ./
RUN flutter pub get

COPY . .
ARG GATEWAY_BASE_URL=/
RUN flutter build web --release \
    --pwa-strategy=none \
    --dart-define=GATEWAY_BASE_URL=${GATEWAY_BASE_URL}

FROM nginx:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/build/web /usr/share/nginx/html

EXPOSE 80
