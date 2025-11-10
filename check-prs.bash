#!/usr/bin/env bash
set -e

if [[ -z ${GITHUB_OUTPUT+x} ]]; then
  GITHUB_OUTPUT=check-prs.log
  OUTPUT_COMPARISON=true
else
  OUTPUT_COMPARISON=false
fi

FAILURES=$(gh pr ls --search 'head:api-docs-' --json 'statusCheckRollup,headRefName' --jq '. | map(select(.statusCheckRollup[].conclusion == "FAILURE")) | .[].headRefName' | sort -u)
# grep -c counts matching lines and the test ensures that grep exit status on non matches is ignored
FAILURE_COUNT=$(echo -n "$FAILURES" | { grep "^.*$" -c || test $? = 1; })
FAILURE_BRANCHES=$(echo "$FAILURES" | paste -sd' ' -)
echo "failure_count=$FAILURE_COUNT" >>"$GITHUB_OUTPUT"
echo "failures=$FAILURE_BRANCHES" >>"$GITHUB_OUTPUT"

COMBINED_PR=$(gh pr ls --search 'head:combined-prs-branch' --json 'url' --jq '.[].url' | sort -u)
echo "combined_pr=$COMBINED_PR" >>"$GITHUB_OUTPUT"

PRS=$(gh pr ls --search 'head:api-docs-' --json 'headRefName' --jq '.[].headRefName' | sort -u)
# grep -c counts matching lines and the test ensures that grep exit status on non matches is ignored
PR_COUNT=$(echo -n "$PRS" | { grep "^.*$" -c || test $? = 1; })
PR_BRANCHES=$(echo "$PRS" | grep -ve "$FAILURE_BRANCHES" | paste -sd' ' -)
echo "pr_count=$PR_COUNT" >>"$GITHUB_OUTPUT"
echo "prs=$PR_BRANCHES" >>"$GITHUB_OUTPUT"

if [[ $OUTPUT_COMPARISON = true ]]; then
  cat "$GITHUB_OUTPUT"
fi
