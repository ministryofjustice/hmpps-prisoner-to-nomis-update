#!/usr/bin/env bash
set -e

if [[ -z ${GITHUB_OUTPUT+x} ]]; then
  GITHUB_OUTPUT=check-prs.log
  OUTPUT_COMPARISON=true
else
  OUTPUT_COMPARISON=false
fi

FAILURES=$(gh pr ls --search 'head:api-docs-' --json 'statusCheckRollup,headRefName' --jq '. | map(select(.statusCheckRollup[].conclusion == "FAILURE")) | .[].headRefName' | sort -u)
FAILURE_COUNT=$(echo "$FAILURES" | wc -l | awk '{print $1}')
FAILURE_BRANCHES=$(echo "$FAILURES" | paste -sd' ' -)
echo "failure_count=$FAILURE_COUNT" >>"$GITHUB_OUTPUT"
echo "failures=$FAILURE_BRANCHES" >>"$GITHUB_OUTPUT"

COMBINED_PR=$(gh pr ls --search 'head:combined-prs-branch' --json 'url' --jq '.[].url' | sort -u)
echo "combined_pr=$COMBINED_PR" >>"$GITHUB_OUTPUT"

PR_COUNT=$(gh pr ls --search 'head:api-docs-' --json 'url' --jq '.[].url' | sort -u | wc -l | awk '{print $1}')
echo "pr_count=$PR_COUNT" >>"$GITHUB_OUTPUT"

if [[ $OUTPUT_COMPARISON = true ]]; then
  cat "$GITHUB_OUTPUT"
fi
