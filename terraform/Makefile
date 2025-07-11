# Makefile for Docker-based OpenTofu operations

.PHONY: help build shell deploy plan apply destroy clean

# Default target
help:
	@echo "Available commands:"
	@echo "  make build          - Build the OpenTofu Docker image"
	@echo "  make shell          - Start interactive OpenTofu shell"
	@echo "  make deploy TAG=v1  - Deploy with specific image tag"
	@echo "  make plan TAG=v1    - Plan deployment with specific image tag"
	@echo "  make apply TAG=v1   - Apply deployment with specific image tag"
	@echo "  make destroy        - Destroy infrastructure"
	@echo "  make clean          - Clean up Docker images and volumes"

# Variables
TAG ?= current
DOCKER_IMAGE = cookbook-opentofu
DOCKER_VOLUME = tofu-cache
TERRAFORM_VOLUME = tofu-terraform

GOOGLE_SERVICE_ACCOUNT_CREDENTIALS = "$(PWD)/no-git/kukbuk-sa-tf-key.json"

# Docker run command template
DOCKER_RUN = docker run --rm -it \
	-v "$(PWD)/..:/workspace" \
	-v "$(HOME)/.config/gcloud:/root/.config/gcloud:ro" \
	-v "$(DOCKER_VOLUME):/root/.terraform.d" \
	-v "$(TERRAFORM_VOLUME):/workspace/terraform/.terraform" \
	-w /workspace/terraform \
	-e GOOGLE_SERVICE_ACCOUNT_CREDENTIALS \
	-e CLOUDSDK_CONFIG=/root/.config/gcloud \
	$(DOCKER_IMAGE)

# Build Docker image
build:
	@echo "Building OpenTofu Docker image..."
	docker build -t $(DOCKER_IMAGE) .

# Build minimal Docker image (without gcloud)
build-minimal:
	@echo "Building minimal OpenTofu Docker image..."
	docker build -f Dockerfile.minimal -t $(DOCKER_IMAGE) .

# Start interactive shell
shell: build
	@echo "Starting OpenTofu Docker shell..."
	$(DOCKER_RUN)

# Initialize OpenTofu
init: build
	@echo "Initializing OpenTofu..."
	$(DOCKER_RUN) -c "tofu init"

# Plan deployment
plan: build
	@echo "Planning deployment with image tag: $(TAG)"
	$(DOCKER_RUN) -c "tofu plan -var='image_tag=$(TAG)'"

# Apply deployment
apply: build
	@echo "Applying deployment with image tag: $(TAG)"
	$(DOCKER_RUN) -c "tofu apply -var='image_tag=$(TAG)' -auto-approve"

# Deploy (plan + apply with confirmation)
deploy: build prepare-function
	@echo "Deploying with image tag: $(TAG)"
	$(DOCKER_RUN) -c "tofu plan -var='image_tag=$(TAG)'"
	@read -p "Do you want to apply these changes? (y/N) " REPLY; \
	if [ "$$REPLY" = "y" ] || [ "$$REPLY" = "Y" ]; then \
		$(DOCKER_RUN) -c "tofu apply -var='image_tag=$(TAG)' -auto-approve"; \
		echo "Deployment completed successfully!"; \
		$(DOCKER_RUN) -c "tofu output"; \
	else \
		echo "Deployment cancelled."; \
	fi

# Destroy infrastructure
destroy: build
	@echo "Destroying infrastructure..."
	@read -p "Are you sure you want to destroy all resources? (y/N) " REPLY; \
	if [ "$$REPLY" = "y" ] || [ "$$REPLY" = "Y" ]; then \
		$(DOCKER_RUN) -c "tofu destroy -auto-approve"; \
	else \
		echo "Destroy cancelled."; \
	fi

# Show outputs
output: build
	@echo "Showing outputs..."
	$(DOCKER_RUN) -c "tofu output"

# Clean up Docker resources
clean:
	@echo "Cleaning up Docker resources..."
	-docker rmi $(DOCKER_IMAGE)
	-docker volume rm $(DOCKER_VOLUME)
	-docker volume rm $(TERRAFORM_VOLUME)
	@echo "Cleanup completed."
