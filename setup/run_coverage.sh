#!/usr/bin/env bash
# run_coverage.sh
# Usage (from repo root): ./setup/run_coverage.sh
# macOS/Linux equivalent of run_coverage.ps1

set -uo pipefail

# Repo root = parent of the directory this script lives in
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"

# Go image per service — must match the go.mod version
SERVICES=(
    "banking-core-service-go"
    "credit-service-go"
    "interbank-service"
    "market-service-go"
    "notification-service-go"
    "saga-orchestrator-service"
    "trading-service-go"
    "user-service-go"
)

# Go image per service (case-based for bash 3.2 compatibility — macOS default)
service_image() {
    case "$1" in
        "credit-service-go")       echo "golang:1.26-alpine" ;;
        "notification-service-go") echo "golang:1.26-alpine" ;;
        *)                         echo "golang:1.25-alpine" ;;
    esac
}

# ANSI colors
C_RESET=$'\033[0m'
C_WHITE=$'\033[97m'
C_GRAY=$'\033[90m'
C_CYAN=$'\033[36m'
C_RED=$'\033[31m'
C_YELLOW=$'\033[33m'
C_GREEN=$'\033[32m'

# Echo the color escape for a coverage value
coverage_color() {
    local val="$1"
    awk -v v="$val" 'BEGIN { if (v < 0) exit 1; if (v < 40) exit 2; if (v < 70) exit 3; exit 4 }'
    case $? in
        1) printf '%s' "$C_GRAY" ;;
        2) printf '%s' "$C_RED" ;;
        3) printf '%s' "$C_YELLOW" ;;
        *) printf '%s' "$C_GREEN" ;;
    esac
}

# Parse a "total:  XX.X%" line into a number; -1 if not found
parse_coverage() {
    echo "$1" | grep -oE '[0-9]+(\.[0-9]+)?%' | head -1 | tr -d '%' || true
}

repeat() { printf '%*s' "$1" '' | tr ' ' "$2"; }

# Result arrays (parallel)
R_SERVICE=()
R_PCTSTR=()
R_PCTCOLOR=()
R_STATUS=()

total=${#SERVICES[@]}
i=0

echo ""
echo "  ${C_WHITE}GO TEST COVERAGE${C_RESET}"
echo "  ${C_GRAY}$(repeat 50 '=')${C_RESET}"
echo ""

for svc in "${SERVICES[@]}"; do
    i=$((i + 1))
    printf '  %s[%d/%d] %s%s%s ... %s' "$C_GRAY" "$i" "$total" "$C_CYAN" "$svc" "$C_GRAY" "$C_RESET"

    image="$(service_image "$svc")"
    raw="$(docker run --rm \
        -v "${ROOT}:/workspace" \
        -e GOWORK=off \
        -w "/workspace/$svc" \
        "$image" \
        sh -c "go test ./... -coverprofile=/tmp/cov.out 2>&1; echo '---COVER---'; go tool cover -func=/tmp/cov.out 2>/dev/null | tail -1" 2>&1)"

    cover_line="$(echo "$raw" | grep "total:" || true)"
    pct="$(parse_coverage "$cover_line")"
    [ -z "$pct" ] && pct="-1"

    if [ "$pct" != "-1" ]; then
        pct_str="${pct}%"
    else
        pct_str="n/a"
    fi

    failed="$(echo "$raw" | grep -E '^(FAIL|--- *FAIL)' || true)"
    failed_count=0
    [ -n "$failed" ] && failed_count="$(echo "$failed" | grep -c '' )"
    has_error="$(echo "$raw" | grep -cE '^(FAIL|build failed|cannot)' || true)"

    pct_color="$(coverage_color "$pct")"

    if [ "$has_error" -gt 0 ] && awk -v v="$pct" 'BEGIN { exit !(v < 0) }'; then
        status="ERROR"
        printf '%sERROR%s\n' "$C_RED" "$C_RESET"
    elif [ "$failed_count" -gt 0 ]; then
        status="FAIL"
        printf '%sFAIL  %s(%s coverage)%s\n' "$C_RED" "$C_GRAY" "$pct_str" "$C_RESET"
    else
        status="OK"
        printf '%sOK    %s(%s coverage)%s\n' "$C_GREEN" "$pct_color" "$pct_str" "$C_RESET"
    fi

    if [ "$failed_count" -gt 0 ]; then
        while IFS= read -r line; do
            printf '         %s%s%s\n' "$C_RED" "$line" "$C_RESET"
        done <<< "$failed"
    fi

    R_SERVICE+=("$svc")
    R_PCTSTR+=("$pct_str")
    R_PCTCOLOR+=("$pct_color")
    R_STATUS+=("$status")
done

echo ""
echo "  ${C_GRAY}$(repeat 50 '=')${C_RESET}"
echo "  ${C_WHITE}SUMMARY${C_RESET}"
echo "  ${C_GRAY}$(repeat 50 '=')${C_RESET}"
echo ""
printf '  %s%-35s %8s   %s%s\n' "$C_GRAY" "Service" "Coverage" "Status" "$C_RESET"
echo "  ${C_GRAY}$(repeat 55 '-')${C_RESET}"

ok_count=0
fail_count=0
for idx in "${!R_SERVICE[@]}"; do
    status="${R_STATUS[$idx]}"
    case "$status" in
        OK)   status_color="$C_GREEN" ;;
        FAIL) status_color="$C_RED" ;;
        *)    status_color="$C_GRAY" ;;
    esac

    printf '  %s%-35s %s%8s   %s%s%s\n' \
        "$C_WHITE" "${R_SERVICE[$idx]}" \
        "${R_PCTCOLOR[$idx]}" "${R_PCTSTR[$idx]}" \
        "$status_color" "$status" "$C_RESET"

    if [ "$status" = "OK" ]; then
        ok_count=$((ok_count + 1))
    else
        fail_count=$((fail_count + 1))
    fi
done

echo ""
echo "  ${C_WHITE}Passed: ${ok_count}   Failed/Error: ${fail_count}${C_RESET}"
echo ""