#!/usr/bin/env bash
set -e

BRANCH=${1?No branch specified}

PR_NUMBER=$(gh pr list -H "$BRANCH" --json number --jq '.[].number')

if [[ "$PR_NUMBER" == "" ]]; then
  echo "No pull request found for branch $BRANCH"
  exit 0
fi

gh pr close --comment "Auto-closing pull request" --delete-branch "$PR_NUMBER"
