#!/bin/bash
# shard-cli.sh - Wrapper script for Shard CLI tool
# Usage: ./shard-cli.sh <command> [options]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CLI_JAR="$SCRIPT_DIR/target/shard-cli.jar"

# Кольори для виводу
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Перевіряємо чи є JAR
if [ ! -f "$CLI_JAR" ]; then
    echo -e "${YELLOW}Building shard-cli...${NC}"
    cd "$SCRIPT_DIR"
    mvn clean package -q
    if [ $? -ne 0 ]; then
        echo -e "${RED}Build failed!${NC}"
        exit 1
    fi
    echo -e "${GREEN}Build successful!${NC}"
    cd "$PROJECT_ROOT"
fi

# Запускаємо CLI з передачею всіх аргументів
# За замовчуванням шукаємо mapping.json в корені проєкту
java --enable-preview -jar "$CLI_JAR" "$@"