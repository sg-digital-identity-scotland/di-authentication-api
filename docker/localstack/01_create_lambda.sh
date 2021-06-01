#!/usr/bin/env bash

set -e

awslocal lambda create-function \
    --function-name my-function \
    --runtime java11 \
    --zip-file fileb:///dist/lambda/lambda.zip \
    --handler uk.gov.di.userinfo.UserInfoHandler \
    --role arn:aws:iam::123456789012:role/service-role/my-lambda-execution-role-123456
