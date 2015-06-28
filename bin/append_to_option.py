#!/usr/bin/env python

"""Append text to an option.
Usage:
    append_to_option.py DELIMITER OPTION APPEND_STRING OPTION_STRING
For example, running
    append_to_option.py , --jars myproject.jar --option1 value1 --jars otherproject.jar --option2 value2
will write to stdout
    --option1 value1 --jars otherproject.jar,myproject.jar --option2 value2
"""

import sys

delimiter = sys.argv[1]
target = sys.argv[2]
append = sys.argv[3]
original = sys.argv[4:]

if original.count(target) > 1:
    sys.stderr.write("Found multiple %s in the option list." % target)
    sys.exit(1)

if original.count(target) == 0:
    original.extend([target, append])
else:  # original.count(target) == 1
    idx = original.index(target)
    new_value = delimiter.join([original[idx + 1], append])
    original[idx + 1] = new_value
sys.stdout.write(' '.join(original))
