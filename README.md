perfscripts
===========

scripts to quickly measure system baseline IO performance

overview
--------

This is meant to be a quick way to get some comparable performance numbers from a
system. The goal is to be able to clone the repo and do minimal configuration before
getting results. As well, the set of tests will be chosen to be useful for a round
assessment of systems, but simple enough to allow for side-by-side comparison with results
from different systems.

dependencies
------------

- [fio](https://github.com/axboe/fio) (could be in the Linux repository as well)

Usage
----

    bin/run-fio-tests [-c config_dir] [-j] -r results_dir -d data_dir [number_cycles]

Parameters are:

- `-r` (required) - full path to directory where results will be stored;
- `-d` (required) - full path to directory where tests will be executed;
- `-c` (optional) - path to directory with configuration files (`./conf/` by default);
- `-j` (optional) - output data in the JSON format instead of human-readable.

The full test battery will run 53 full-speed microbenchmarks, with IO workloads simulating
various levels of streaming reads, writes, random reads, and combinations of these.

Each test is also coded with a test id, to make cross-comparisons and discussion easier.

Quick tests
-----------

If you want to run an partial set of tests, you can use the syntax

    bin/run-fio-tests -r results_dir -d data_dir 10

This will run the most basic tests first, filling in gaps in the results as the tests continue.
Details on the test order are in [test_order.lst](conf/test_order.lst)

