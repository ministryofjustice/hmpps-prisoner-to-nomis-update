#!/usr/bin/env bash
set -e

PROJECT=${1?No project specified}
PROJECT_CAMEL=$(echo "$PROJECT" | sed -r 's/(^|-)([a-z])/\U\2/g')
MODEL_TASK="build${PROJECT_CAMEL}ApiModel"
JSON_TASK="write${PROJECT_CAMEL}Json"
SPECS_JSON="openapi-specs/$PROJECT-api-docs.json"
if [[ -z ${GITHUB_OUTPUT+x} ]]; then
  GITHUB_OUTPUT=output
  OUTPUT_COMPARISON=true
else
  OUTPUT_COMPARISON=false
fi

OLD_VERSION=$(jq -r .info.version "$SPECS_JSON")
echo "old_version=$OLD_VERSION" >>"$GITHUB_OUTPUT"

# build the model and grab new json
./gradlew clean "$MODEL_TASK" "$JSON_TASK"

mv "build/generated/$PROJECT" build/model_copy

NEW_VERSION=$(jq -r .info.version "$SPECS_JSON")
echo "new_version=$NEW_VERSION" >>"$GITHUB_OUTPUT"

if [[ "$OLD_VERSION" == "$NEW_VERSION" ]]; then
  echo "Version of $OLD_VERSION is unchanged"
  echo "differences=false" >>"$GITHUB_OUTPUT"
  exit 0
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

if [[ $OUTPUT_COMPARISON = true ]]; then
  cat "$GITHUB_OUTPUT"
fi
  
