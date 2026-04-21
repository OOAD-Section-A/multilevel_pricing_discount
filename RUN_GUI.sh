#!/bin/bash
set -euo pipefail

echo "Starting Pricing Subsystem GUI..."
echo "Connecting to MySQL database OOAD..."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_URL="${DB_URL:-jdbc:mysql://localhost:3306/OOAD}"
DB_USERNAME="${DB_USERNAME:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
PRICING_SEED_DEMO_DATA="${PRICING_SEED_DEMO_DATA:-false}"

cd "$SCRIPT_DIR"

echo "Building project..."
mvn -q -DskipTests package

echo "Launching pricing GUI..."
java \
  -Ddb.url="$DB_URL" \
  -Ddb.username="$DB_USERNAME" \
  -Ddb.password="$DB_PASSWORD" \
  -Dpricing.seed.demo="$PRICING_SEED_DEMO_DATA" \
  -cp "common/target/classes:pricing/target/classes:lib/*" \
  com.pricingos.pricing.gui.PricingSubsystemGUI
