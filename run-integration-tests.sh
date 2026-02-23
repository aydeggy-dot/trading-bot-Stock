#!/usr/bin/env bash
##############################################################################
# NGX Trading Bot — Production Integration Test Runner
#
# Usage:
#   ./run-integration-tests.sh              # Run all steps
#   ./run-integration-tests.sh --step 1     # Run only Step 1 (PostgreSQL)
#   ./run-integration-tests.sh --step 2     # Run only Step 2 (EODHD)
#   ./run-integration-tests.sh --step 1-6   # Run Steps 1 through 6
#   ./run-integration-tests.sh --no-browser # Skip browser tests (Steps 7-8)
#
# Prerequisites:
#   1. .env file with real credentials (copy from .env.example)
#   2. Docker Desktop running
#   3. docker compose up -d postgres waha
#   4. For Step 4: Open http://localhost:3000, scan QR to link WhatsApp
##############################################################################

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse .env file
if [ -f .env ]; then
    echo -e "${BLUE}Loading .env file...${NC}"
    set -a
    source .env
    set +a
else
    echo -e "${RED}ERROR: .env file not found!${NC}"
    echo "Copy .env.example to .env and fill in your credentials."
    exit 1
fi

# Parse arguments
STEP=""
NO_BROWSER=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --step) STEP="$2"; shift 2 ;;
        --no-browser) NO_BROWSER=true; shift ;;
        --help|-h) echo "Usage: $0 [--step N|N-M] [--no-browser]"; exit 0 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║     NGX Trading Bot — Production Integration Tests          ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  Step 1:  PostgreSQL + Flyway migrations                    ║"
echo "║  Step 2:  EODHD Market Data API                             ║"
echo "║  Step 3:  Telegram Notifications                            ║"
echo "║  Step 4:  WAHA / WhatsApp                                   ║"
echo "║  Step 5:  News Scrapers (6 sources)                         ║"
echo "║  Step 6:  Claude AI API                                     ║"
echo "║  Step 7:  Meritrade Browser Login                           ║"
echo "║  Step 8:  Meritrade Paper Trade (1 share)                   ║"
echo "║  Step 9:  Backtest with Real Data                           ║"
echo "║  Step 10: Dashboard REST API                                ║"
echo "║  Step 11: End-to-End Flow                                   ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# Map step numbers to test class names
declare -A STEP_CLASSES=(
    [1]="Step01_PostgresFlywayIT"
    [2]="Step02_EodhdApiIT"
    [3]="Step03_TelegramIT"
    [4]="Step04_WhatsAppIT"
    [5]="Step05_ScrapersIT"
    [6]="Step06_ClaudeAiIT"
    [7]="Step07_MeritradeLoginIT"
    [8]="Step08_PaperTradeIT"
    [9]="Step09_BacktestIT"
    [10]="Step10_DashboardIT"
    [11]="Step11_EndToEndIT"
)

run_step() {
    local step_num=$1
    local class_name="${STEP_CLASSES[$step_num]}"

    if [ "$NO_BROWSER" = true ] && { [ "$step_num" -eq 7 ] || [ "$step_num" -eq 8 ]; }; then
        echo -e "${YELLOW}⏭ Skipping Step $step_num ($class_name) — browser tests disabled${NC}"
        return 0
    fi

    echo -e "${BLUE}▶ Running Step $step_num: $class_name${NC}"
    echo "────────────────────────────────────────────────"

    if mvn test \
        -Dspring.profiles.active=integration \
        -Dtest="com.ngxbot.integration.$class_name" \
        -Dgroups=integration \
        -pl . \
        --no-transfer-progress \
        2>&1 | tee "/tmp/ngx-it-step${step_num}.log"; then
        echo -e "${GREEN}✅ Step $step_num PASSED${NC}"
        return 0
    else
        echo -e "${RED}❌ Step $step_num FAILED — see /tmp/ngx-it-step${step_num}.log${NC}"
        return 1
    fi
}

# Determine which steps to run
if [ -n "$STEP" ]; then
    if [[ "$STEP" == *-* ]]; then
        # Range: e.g., "1-6"
        IFS='-' read -r START END <<< "$STEP"
        for i in $(seq "$START" "$END"); do
            run_step "$i"
            echo ""
        done
    else
        # Single step
        run_step "$STEP"
    fi
else
    # Run all steps sequentially
    PASSED=0
    FAILED=0
    SKIPPED=0

    for i in $(seq 1 11); do
        if run_step "$i"; then
            ((PASSED++))
        else
            ((FAILED++))
            # Don't fail fast — continue to gather full results
        fi
        echo ""
    done

    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║                    INTEGRATION TEST SUMMARY                 ║"
    echo "╠══════════════════════════════════════════════════════════════╣"
    echo -e "║  ${GREEN}Passed:  $PASSED${NC}                                                ║"
    echo -e "║  ${RED}Failed:  $FAILED${NC}                                                ║"
    echo "╚══════════════════════════════════════════════════════════════╝"

    if [ $FAILED -gt 0 ]; then
        echo -e "${RED}Some integration tests failed. Check logs in /tmp/ngx-it-step*.log${NC}"
        exit 1
    else
        echo -e "${GREEN}All integration tests passed! The bot is ready for production.${NC}"
    fi
fi
