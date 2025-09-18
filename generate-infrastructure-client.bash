#!/bin/bash
IGNORE_FILE=openapi-generator-ignore-nomis-prisoner
INFRA_DIR=src/main/kotlin/org/openapitools/client/infrastructure
# enable generation of infrastructure client
grep -v '/client/infrastructure' $IGNORE_FILE > temp && mv temp $IGNORE_FILE

# run the generator
./gradlew clean buildNomisPrisonerApiModel

# move into our source directory
mv build/generated/nomis-prisoner/$INFRA_DIR/*.kt $INFRA_DIR

# and remove protected from the function
sed "s/protected fun/fun/" $INFRA_DIR/ApiClient.kt > temp && mv temp $INFRA_DIR/ApiClient.kt

# add back in exclusion of client infrastructure generation
echo "**/client/infrastructure/" >> $IGNORE_FILE

# and format so that we can see easily what has changed
./gradlew ktlintFormat
