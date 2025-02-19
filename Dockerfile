FROM --platform=$BUILDPLATFORM ghcr.io/graalvm/native-image-community:21 AS builder
WORKDIR /app
RUN microdnf install findutils
COPY . .
RUN ./gradlew nativeCompile
RUN ls -la build/native/nativeCompile/

FROM --platform=$BUILDPLATFORM ubuntu:22.04
WORKDIR /app
COPY --from=builder /app/build/native/nativeCompile/cookbook .
RUN ls -la
EXPOSE 8080
ENTRYPOINT ["./cookbook"]