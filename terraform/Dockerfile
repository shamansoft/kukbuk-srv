FROM ghcr.io/opentofu/opentofu:minimal AS tofu

FROM alpine:3.20

# Copy the tofu binary from the minimal image
COPY --from=tofu /usr/local/bin/tofu /usr/local/bin/tofu

# Add required tools for GCP deployment
RUN apk add --no-cache \
    git \
    curl \
    bash \
    zip \
    python3 \
    py3-pip

# Install Google Cloud SDK using the official installation method
RUN curl -sSL https://sdk.cloud.google.com | bash -s -- --disable-prompts --install-dir=/usr/local

# Add gcloud to PATH
ENV PATH="/usr/local/google-cloud-sdk/bin:${PATH}"

# Set up gcloud
RUN gcloud config set core/disable_usage_reporting true \
    && gcloud config set component_manager/disable_update_check true

# Your application setup
WORKDIR /workspace

# Copy terraform configuration
COPY . .

# Set entrypoint
ENTRYPOINT ["/bin/bash"]