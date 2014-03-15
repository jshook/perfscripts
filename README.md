perfscripts
===========

scripts to quickly measure system baseline performance

overview
--------

This is mean to be a quick way to get some comparable performance numbers
from a system. The goal is to be able to clone the repo and do minimal
configuration before getting results. As well, the set of tests will
be chosen to be useful for a round assessment of systems, but simple
enough to allow for side-by-side comparison with results from different
systems.

dependencies
------------

* fio

usage
----

Modify env.sh to fit your system paths, and source it into your shell, then bin/run-fio-tests
