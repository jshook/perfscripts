---
weight: 20
title: How it works
menu:
  main:
      parent: How it works
      identifier: How it works 
      weight: 201
---

The tests will run automatically on deploy using the startup scripts. See Build and run.

## FIO

fio is an open source commandline io benchmarking tool, for details see the fio website: https://fio.readthedocs.io/en/latest/

## perfscripts

perfscripts is a bash utility that will run a suite of disk tests using fio and parse the json output response.

## fio vis

fio vis is a web visualizer for perfscripts that allows users to compare their test results with those of other systems.
