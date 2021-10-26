# (C) Copyright IBM Corp. 2020
#
# SPDX-License-Identifier: Apache-2.0

#!/usr/bin/env bash

echo 'Run Nightly Tests'

rspec test/nightly --tag ~@broken --format documentation --format RspecJunitFormatter --out nightlytest.xml
