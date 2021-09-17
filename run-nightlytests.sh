#!/usr/bin/env bash

echo 'Run Nightly Tests'

scriptDir=$(dirname "$0")
rspec $scriptDir/test/nightly/ --tag ~@broken --format documentation --format RspecJunitFormatter --out nightlytest.xml
