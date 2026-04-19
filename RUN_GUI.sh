#!/bin/bash
# Script to run the Pricing Subsystem GUI via Maven

echo "Starting Pricing Subsystem GUI..."
echo "Connecting to MySQL database OOAD..."

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$SCRIPT_DIR/pricing"

echo "Resolving dependencies..."
mvn dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=cp.txt

# Extract maven classpath
MAVEN_CP=$(cat cp.txt)

echo "Launching via Java..."
java -Ddb.url=jdbc:mysql://localhost:3306/OOAD -Ddb.username=root -Ddb.password=Moneyplant1 -cp "target/classes:../lib/*:../common/target/classes:../resources:$MAVEN_CP" com.pricingos.pricing.gui.PricingSubsystemGUI
