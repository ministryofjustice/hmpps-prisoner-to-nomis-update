#!/usr/bin/env bash
aws --endpoint-url=http://localhost:4566 sns publish \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:hmpps_domain_events \
    --message '{"eventType":"COURT_REGISTER_UPDATE","id":"BRMNCC"}' \
    --message-attributes '{"eventType" : { "DataType":"String", "StringValue":"COURT_REGISTER_UPDATE"}}'
