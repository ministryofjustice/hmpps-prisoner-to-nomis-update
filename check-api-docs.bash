#!/usr/bin/env bash
set -e

JSON_TASK=${1?No json task specified}
WORKING_DIR=${2:-.}
PROJECT=$(echo "$JSON_TASK" | sed -e 's/^write//' -e 's/Json$//' -e 's/.*/\l&/' | sed -r 's/([a-z0-9])([A-Z])/\1-\L\2/g' )
PROJECT_CAMEL=$(echo "$PROJECT" | sed -r 's/(^|-)([a-z])/\U\2/g')
MODEL_TASK="build${PROJECT_CAMEL}ApiModel"
PROD_VERSION_TASK="read${PROJECT_CAMEL}ProductionVersion"
SPECS_JSON="${WORKING_DIR}/openapi-specs/$PROJECT-api-docs.json"
if [[ -z ${GITHUB_OUTPUT+x} ]]; then
  GITHUB_OUTPUT=check-api-docs.log
  OUTPUT_COMPARISON=true
else
  OUTPUT_COMPARISON=false
fi
export _JAVA_OPTIONS="-Xmx768m -XX:ParallelGCThreads=2 -XX:ConcGCThreads=2 -XX:ParallelGCThreads=2 -Djava.util.concurrent.ForkJoinPool.common.parallelism=2 -Dorg.gradle.daemon=false -Dorg.gradle.jvmargs=-Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=1"

OLD_VERSION=$(jq -r .info.version "$SPECS_JSON")
echo "old_version=$OLD_VERSION" >>"$GITHUB_OUTPUT"

# build the model and grab new json
./gradlew "$MODEL_TASK" "$JSON_TASK"

mv "${WORKING_DIR}/build/generated/$PROJECT" "${WORKING_DIR}/build/model_copy"

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
if ! diff -r build/model_copy "${WORKING_DIR}/build/generated/$PROJECT" > "${WORKING_DIR}/build/api.diff"; then
  echo "Found differences between old ($OLD_VERSION) and new ($NEW_VERSION) models"
  echo "differences=true" >>"$GITHUB_OUTPUT"
  PROD_VERSION=$(./gradlew -q "$PROD_VERSION_TASK" | grep -v 'Ignoring init script')
  echo "Found production version of $PROD_VERSION, writing to github output"
  echo "production_version=$PROD_VERSION" >>"$GITHUB_OUTPUT"
else
  echo "No differences found"
  echo "differences=false" >>"$GITHUB_OUTPUT"
fi

if [[ $OUTPUT_COMPARISON = true ]]; then
  cat "$GITHUB_OUTPUT"
fi
  
