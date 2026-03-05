#! /usr/bin/env bash
if [[ -z "$CI" ]];then
  ktlint 'apps/**/src/main/**/*.kt' '*/src/test/**/*.kt' --reporter=plain?group_by_file --color $@
  ktlint 'libs/**/src/main/**/*.kt' '*/src/test/**/*.kt' --reporter=plain?group_by_file --color $@
  ktlint 'buildSrc/src/main/**/*.kt' '*/src/test/**/*.kt' --reporter=plain?group_by_file --color $@
else
  ktlint 'apps/**/src/main/**/*.kt' '*/src/test/**/*.kt' --reporter=plain?group_by_file
  ktlint 'libs/**/src/main/**/*.kt' '*/src/test/**/*.kt' --reporter=plain?group_by_file
  ktlint 'buildSrc/src/main/**/*.kt' '*/src/test/**/*.kt' --reporter=plain?group_by_file
fi