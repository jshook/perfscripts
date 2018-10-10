---
title: fio vis
type: index
weight: 10
---

The fio vis asset (also known as `perfscripts`) is a disk io test harness and visualizer built ontop of the open source io testing tool `fio`. Note: the full suite takes about 1.5 hours to run depending on disk performance.

### Motivation

The IO subsystem is arguably the most important OS subsystem for a durable transactional database like DSE. When selecting hardware, the best choice tends to be local disks (either spinning or ssd's). 
This tool is meant to help you compare the performance of your disks against others as a baseline for understanding their impact on DSE performance.

### What is included?

This field asset includes the following:

* perfscripts harness for standard fio benchmarks 
* web visualizer that parses fio json outputs and allows comparisons
* a set of benchmarks against common IO hardware for comparison

### Business Take Aways

Implementations of DSE require hardware to run on and companies often make large investments in SANs / etc. In order to make informed commerical decisions about hardware procurement, it is important to understand the technical iplications of these choices. Note, a use case's SLA should mandate whether a hardware selection is "good enough".

### Technical Take Aways

Any shared disk (network attached) will have costs in terms of predictibility of tail latencies (given that IO must incur a network hop). This combined with availability implications (SANs can be a SPOF) leads to local volume recommendations for most mission critical use cases with tight SLAs. Performing a suite of fio tests and comparing the results (not only in terms of throughput but in terms of latency predictability) can help users make better hardware decisions.
