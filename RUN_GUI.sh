#!/bin/bash
# Script to run the Pricing Subsystem GUI

echo "Starting Pricing Subsystem GUI..."
echo "Connecting to MySQL database OOAD..."

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$SCRIPT_DIR/pricing"

java -cp "target/classes:../lib/database-module-1.0.0-SNAPSHOT-standalone.jar:../lib/scm-exception-foundation.jar:../common/target/classes" \
     com.pricingos.pricing.gui.PricingSubsystemGUI
