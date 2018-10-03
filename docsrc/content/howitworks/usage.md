---
weight: 20
title: Usage
menu:
  main:
      parent: How it works
      identifier: Usage
      weight: 201
---

The tests will run automatically on deploy using the startup scripts. See Build and run.

## FIO

fio is an open source commandline io benchmarking tool, for details see the fio website: https://fio.readthedocs.io/en/latest/

## perfscripts

perfscripts is a bash utility that will run a suite of disk tests using fio and parse the json output response. The entire test suite includes 53 tests and takes about half an hour to run depending on your disks.

## fio vis

fio vis is a web visualizer for perfscripts that allows users to compare their test results with those of other systems.
