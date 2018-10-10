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
	- Tested with fio 3.1 installed on CentOS 7.4: `yum install fio`
 	- **The UI will not work with fio 2.x JSON output**

Usage
----

    bin/run-fio-tests [-c config_dir] [-n] [-u] -r results_dir -d data_dir [number_cycles]

Parameters are:

- `-r` (required) - full path to directory where results will be stored;
- `-d` (required) - full path to directory where tests will be executed;
- `-c` (optional) - path to directory with configuration files (`./conf/` by default);
- `-n` (optional) - output results in "normal", human-readable format;
- `-u` (optional) - don't perform tests, but upgrade the existing results with UI
  improvements.  Requires `-r` paramater.

The full test battery will run 53 full-speed microbenchmarks, with IO workloads simulating
various levels of streaming reads, writes, random reads, and combinations of these.

Each test is also coded with a test id, to make cross-comparisons and discussion easier.

To run all of the tests: 
	
	bin/run-fio-tests -r /path/to/results_dir -d /path/to/data_dir 53

In addition of the raw results, you can open up index.html in a browser to see charts of all of the tests that were run. 

If the tests were run on a remote machine you can run:
	
	cd /path/to/results_dir
	python -m SimpleHTTPServer
	Serving HTTP on 0.0.0.0 port 8000 ...
	

demo results: [http://fiovis.site44.com](http://fiovis.site44.com)

![](http://fiovis.site44.com/screenshots/fio_vis.png)

Quick tests
-----------

If you want to run an partial set of tests, you can use the syntax

    bin/run-fio-tests -r /path/to/results_dir -d /path/to/data_dir 10



This will run the most basic tests first, filling in gaps in the results as the tests continue.
Details on the test order are in [test_order.lst](conf/test_order.lst)

