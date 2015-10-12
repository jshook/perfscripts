perfscripts
===========

scripts to quickly measure system baseline IO performance

overview
--------

This is meant to be a quick way to get some comparable performance numbers
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

Modify env.sh to fit your system paths, and source it into your shell, then

    bin/run-fio-tests

The full test battery will run 53 full-speed microbenchmarks, with IO workloads
simulating various levels of streaming reads, writes, random reads, and combinations
of these.

quick tests
-----------

If you want to run an partial set of tests, you can use the syntax

    bin/run-fio-tests 10

This will run the most basic tests first, filling in gaps in the results as the tests continue.
Details on the test order are in [test_order.lst](conf/test_order.lst)

