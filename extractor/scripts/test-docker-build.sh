#!/bin/bash
# Test script to validate Docker build configuration for Spring Boot 4 migration
# Tests both JVM and native image Dockerfiles

set -e

echo "=== Docker Build Configuration Tests ==="
echo ""

# Test 1: Verify Dockerfile.jvm uses Java 25 JRE
echo "Test 1: Checking Dockerfile.jvm for Java 25..."
if grep -q "eclipse-temurin:25-jre" ../Dockerfile.jvm; then
    echo "✅ Dockerfile.jvm uses Java 25 JRE"
else
    echo "❌ Dockerfile.jvm does not use Java 25 JRE"
    exit 1
fi

# Test 2: Verify Dockerfile.native uses GraalVM 25
echo "Test 2: Checking Dockerfile.native for GraalVM 25..."
if grep -q "graalvm/native-image-community:25" ../Dockerfile.native; then
    echo "✅ Dockerfile.native uses GraalVM 25"
else
    echo "❌ Dockerfile.native does not use GraalVM 25"
    exit 1
fi

# Test 3: Verify Dockerfile.native memory settings for Java 25
echo "Test 3: Checking Dockerfile.native memory optimization settings..."
if grep -q "JAVA_OPTS.*Xmx.*MaxMetaspaceSize" ../Dockerfile.native; then
    echo "✅ Dockerfile.native has memory optimization settings"
else
    echo "❌ Dockerfile.native missing memory optimization settings"
    exit 1
fi

# Test 4: Verify Dockerfile.jvm has Java 25 flags
echo "Test 4: Checking Dockerfile.jvm for Java 25 flags..."
if grep -q "enable-native-access=ALL-UNNAMED" ../Dockerfile.jvm; then
    echo "✅ Dockerfile.jvm has Java 25 native access flag"
else
    echo "❌ Dockerfile.jvm missing Java 25 native access flag"
    exit 1
fi

# Test 5: Verify build.sh handles native flag
echo "Test 5: Checking build.sh for native build support..."
if grep -q "NATIVE_FLAG" build.sh; then
    echo "✅ build.sh supports native build flag"
else
    echo "❌ build.sh missing native build flag support"
    exit 1
fi

# Test 6: Verify build.sh uses correct Dockerfile paths
echo "Test 6: Checking build.sh Dockerfile paths..."
if grep -q 'DOCKERFILE="extractor/Dockerfile.native"' build.sh && \
   grep -q 'DOCKERFILE="extractor/Dockerfile.jvm"' build.sh; then
    echo "✅ build.sh has correct Dockerfile paths"
else
    echo "❌ build.sh has incorrect Dockerfile paths"
    exit 1
fi

echo ""
echo "=== All Docker Build Configuration Tests Passed ✅ ==="
echo ""
echo "Note: Actual Docker builds require:"
echo "  - JVM build: ./build.sh <tag>"
echo "  - Native build: ./build.sh <tag> --native --memory=12g"
echo "  - Native builds require x86-64-v3 CPU (tested in CI/CD)"
