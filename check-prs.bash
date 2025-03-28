#!/usr/bin/env bash
set -e

if [[ -z ${GITHUB_OUTPUT+x} ]]; then
  GITHUB_OUTPUT=check-prs.log
  OUTPUT_COMPARISON=true
else
  OUTPUT_COMPARISON=false
fi

FAILING_URLS=$(gh pr ls --search 'head:api-docs-' --json 'statusCheckRollup,url' --jq '. | map(select(.statusCheckRollup[].state == "FAILURE")) | .[].url' | sort -u)
echo "failing_prs=$FAILING_URLS" >>"$GITHUB_OUTPUT"

URLS=$(gh pr ls --search 'head:api-docs-' --json 'url' --jq '.[].url' | sort -u)
echo "all_prs=$URLS" >>"$GITHUB_OUTPUT"

if [[ $OUTPUT_COMPARISON = true ]]; then
  cat "$GITHUB_OUTPUT"
fi
