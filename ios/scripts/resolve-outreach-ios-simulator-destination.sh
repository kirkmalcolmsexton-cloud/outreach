#!/usr/bin/env bash
# Print xcodebuild -destination for the Outreach default iOS Simulator (same as android/scripts/resolve-outreach-emulator-serial.sh).
#
#   export OUTREACH_DESTINATION="$(bash ios/scripts/resolve-outreach-ios-simulator-destination.sh)"
#   bash ios/scripts/build.sh build
#
# Override device name: OUTREACH_IOS_SIM_NAME="My Sim" bash ios/scripts/resolve-outreach-ios-simulator-destination.sh
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/simulator.sh" destination
