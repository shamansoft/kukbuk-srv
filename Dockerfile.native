FROM --platform=linux/amd64 ghcr.io/graalvm/native-image-community:21 AS builder

WORKDIR /app
RUN microdnf install findutils

# Option 1: Dependency caching
COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY gradlew ./
RUN ./gradlew dependencies

COPY . .
#RUN --mount=type=cache,target=/root/.gradle \
#    ./gradlew nativeCompile

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew nativeCompile \
    -Porg.graalvm.buildtools.native.targetPlatform=linux-amd64

FROM --platform=linux/amd64 ubuntu:22.04
WORKDIR /app
COPY --from=builder /app/build/native/nativeCompile/cookbook .
# Expose the port (though Cloud Run handles this, it's good practice)
EXPOSE $PORT

# Set the startup command, using the PORT environment variable
CMD ["./cookbook", "--server.port=${PORT}"]