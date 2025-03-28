#!/usr/bin/env bash
set -e

if [[ -z ${GITHUB_OUTPUT+x} ]]; then
  GITHUB_OUTPUT=check-prs.log
  OUTPUT_COMPARISON=true
else
  OUTPUT_COMPARISON=false
fi

FAILING_URLS=$(gh pr ls --search 'head:api-docs-' --json 'statusCheckRollup,url' --jq '. | map(select(.statusCheckRollup[].state == "FAILURE")) | .[].url' | sort -u)
if [[ $FAILING_URLS != "" ]]; then
  {
    echo "failing_prs<<EOF"
    echo "$FAILING_URLS"
    echo "EOF"
  } >>"$GITHUB_OUTPUT"
else
  echo "failing_prs=" >>"$GITHUB_OUTPUT"
fi

URLS=$(gh pr ls --search 'head:api-docs-' --json 'url' --jq '.[].url' | sort -u)
if [[ $URLS != "" ]]; then
  {
    echo "all_prs<<EOF"
    echo "$URLS"
    echo "EOF"
  } >>"$GITHUB_OUTPUT"
else
  echo "all_prs=" >>"$GITHUB_OUTPUT"
fi

if [[ $OUTPUT_COMPARISON = true ]]; then
  cat "$GITHUB_OUTPUT"
fi
