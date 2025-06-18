#!/bin/bash
set -eu

get_auth_token() {
  if (( $# < 3 )); then
    echo "Error: Missing required parameter(s)." >&2
    echo "Usage: get_auth_token <auth_host> <client_id> <client_secret>" >&2
    return 1
  fi

  local auth_host=$1
  local client_id=$2
  local client_secret=$3

  create_secret() {
    local client_id=$1
    local client_secret=$2
    echo -n "$client_id:$client_secret" | base64 -w 0
  }

  call_auth() {
    local auth_host=$1
    local secret=$1
    curl -s -X POST "$auth_host/oauth/token?grant_type=client_credentials" \
      -H 'Content-Type: application/json' \
      -H "Authorization: Basic $secret"
  }

  extract_token() {
    local jwt=$1
    echo -n $jwt | sed -n 's/.*"access_token": *"\([^"]*\)".*/\1/p'
  }

  local secret=$(create_secret client_id client_secret)
  local response=$(call_auth auth_host secret)
  echo $(extract_token response)
}
