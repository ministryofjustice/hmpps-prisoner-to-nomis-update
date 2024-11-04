#!/usr/bin/env bash
set -e

PROJECT=${1?No project specified}
MODEL_TASK="build${PROJECT^}ApiModel"
SPECS_JSON="openapi-specs/$PROJECT-api-docs.json"

declare -A URLS=(
  ["alerts"]="https://alerts-api-dev.hmpps.service.justice.gov.uk"
  ["csip"]="https://csip-api-dev.hmpps.service.justice.gov.uk"
)

# build the model
./gradlew clean "$MODEL_TASK"

mv "build/generated/$PROJECT" build/model_copy

OLD_VERSION=$(jq -r .info.version "$SPECS_JSON")
echo "old_version=$OLD_VERSION" >>"$GITHUB_OUTPUT"

# grab latest version
curl "${URLS[$PROJECT]}/v3/api-docs" | jq . >"$SPECS_JSON"

NEW_VERSION=$(jq -r .info.version "$SPECS_JSON")
echo "new_version=$NEW_VERSION" >>"$GITHUB_OUTPUT"

if [[ "$OLD_VERSION" == "$NEW_VERSION" ]]; then
  echo "Version of $OLD_VERSION is unchanged"
fi

# build the model again
./gradlew "$MODEL_TASK"

# and compare
if ! diff -r build/model_copy "build/generated/$PROJECT" >"build/api.diff"; then
  echo "Found differences between old ($OLD_VERSION) and new ($NEW_VERSION) models"
  echo "differences=true" >>"$GITHUB_OUTPUT"
else
  echo "No differences found"
  echo "differences=false" >>"$GITHUB_OUTPUT"
fi
