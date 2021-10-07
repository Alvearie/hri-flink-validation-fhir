# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0

#!/usr/bin/env bash

echo 'Run IVT Tests'
rspec test/spec --tag ~@broken --format documentation --format RspecJunitFormatter --out ivttest.xml