# Cookbook Project

A multi-language project for managing and processing recipes with a Java-based backend and Node.js utilities. It includes a Spring Boot application, Gradle build scripts, JavaScript token handling, Docker support, and deployment scripts for Google Cloud Run.

## Technologies Used

- **Java** with Spring Boot and Jakarta for the REST API
- **Gradle** for building and dependency management
- **JavaScript** and **npm** for authentication utilities
- **Docker** for containerization
- **Shell Scripts** for building and deploying on Google Cloud Platform

## Build & Deployment

- Build the project using Gradle:

  ```bash
  ./gradlew build
  ```
- Use the scripts in the extractor/scripts/ directory for building, deploying, and running Docker containers:


    extractor/scripts/build.sh: Build the Docker image (supports both JVM and native builds)
    extractor/scripts/deploy.sh: Deploy the image to Cloud Run
    extractor/scripts/release.sh: Chain build and deploy
    extractor/scripts/run-docker.sh: Run a local container

## Version Management
The project version is specified in build.gradle and automatically updated via the script in extractor/gcp/version-updater.sh. The script extracts the current version, increments the patch number, updates the file, and verifies the update.

## Environment Variables
Environment variables such as the API key are located in the .env file. Example:
```
COOKBOOK_GEMINI_API_KEY=AIzaSyCkGNz6ImDm4uqFx-mgikNyO4o_koyX5Hg
```

# Token Broker
The repository also includes Node.js code that:


Fetches ID tokens from Google Cloud using the google-auth-library
Validates OAuth tokens by checking against Google token info endpoints