# Cross-System Analysis

This document describes the method used to compare the results of different runs of github.com/jshook/perfscripts.

## Usage

Regardless of the implementation stack or language, the command to run a report should be simply './analyze'

## Terms

The individual "*.fio.json" files are known as _workloads_. Workload files may have multiple results within them known as _components_.

## Stage 1, Prepare Report target

If there is already a subdirectory matching the glob 'report_*', then use it. Otherwise create one with the name "report". The report directory should be an optional option "--report-dir", and if it already exists, then the '-U' option for "update" must be provided to avoid accidentally overwriting a previous report, but only if it isn't the default "report" name.

## Stage 1, Enumerate Results

In order to facilitate proper grouping and labeling of results, the following directory structure is used:
1. Directories which contain any "*.fio.json" files are included. Each directory included this way is known as a "system path", and should be retained as such.
2. All system paths are grouped according to common leading path components.
3. For each group of paths which has common leading components, a "system profile" is created. System profiles have system paths. and System profile names.
   1. The system profile name is created from the common leading path components for all the systems included, with basic sanitization of the name to avoid path seperator characters.
   2. The system profile path is simply the path which is the common prefix among all system paths.
4. Each system path is used to derive an associated system name:
   1. Each system path is relativized with respect to the associated system profile path.
   2. The name of the system is then taken as the relativized system path, but with the common leading and trailing path components removed, among all systems within the system profile.
   3. The system name is also sanitized.
   4. The system name and the system path are kept separately.
5. Thus we have:
   1. Each system profile, consisting of
      a. a system profile name
      b. a system profile path
      c. a set of associated systems, each consisting of
         1. a system path
         2. a system name
6. It should be easy to find the "*.fio.json" files for a given system by simply resolving the system path within the system profile path. Within the resolved directory path, the "*.fio.json" files should be visibile.
8. A manifest.json file is created in the report directory with all these details in hierarchical structure.
9. A manifest.md file is created in the report directory with the same details, but with a human-friendly layout.

## Stage 2, Single Directory Analysis

Each included system with a system profile gets analyzed separately.

### Single Directory Structure

The "*.fio.json" files within a directory contain a set of fio test results in the canonical fio json output format.
These files are named specifically to identify the access patterns, and are organized by an identifier and associated series name.
Here are the naming details for each type of workload:
* `randread-000-512.fio.json` is a "random read" workload with a test id of "000" (series 000), with a request size of 512 bytes.
* `randread-007-64k.fio.json` is a "random read" workload, with a test id of "007" (series 000), with a request size of 64k bytes.
* `seqread-100-32g.fio.json` is a "sequential read" workload, with test id "100" (series 100), with a 32GB file size.
* `seqwrite-200-32g.fio.json` is a "sequential write" workload, with a test id of "200" (series 200), with a 32GB file size.
* `mixed-301-1to4k_10Mseq.fio.json` is a "mixed" workload, containing multiple components: sequential reads, sequential writes, and random io. It has an id of "301" (series 300), and a request size ranging between 1k and 4k bytes, where the sequential reads and writes are limited to 10MB/s. These files combine the workload components of the earlier series, thus have multiple component results per file.

When interpreting the block size indicators in randread file names, the format may be one of `<min><suffix>to<max><suffix>` or `<min>to<max>suffix`. In the latter case, the suffix applies to both numbers.

### Report Processing

For stage 2 report generation, a separate report is created in the report directory for each system in each system profile. The name should include the system profile name and the system name, concatenated with a double underscore.

Key metrics for randread workloads are throughput, latency, and ops/s.
Key metrics for seqread and seqwrite workloads are throughput.
Key metrics for mixed workloads include througput, latency, and ops/s of the randread portion, throughput of the sequential portions.

Each system's analysis should proceed with these specific steps:
1. Determine optimal blocksize: The randread results are analyzed to determine which blocksize achieves the highest throughput for that system.
2. Determine matching mixed workload: According to the optimal randread workload, a mixed workload series is selected with the closest average blocksize.
3. Knee-point analysis of mixed workloads: The selected mixed workload series is analyzed, comparing the knee points for each of the completion latencies, slewing across the stream throttling values. An unicode-based sparkline is created for each of the quantiles, showing order of magnitude changes from one streaming limit to the next.
4. The point at which the p99 latency increases more dramatically between two tests is the knee point. Of these two, the mixed workload with the lower latency is considered the "optimal mixed workload". The one with the higher latency is considered the "sub-optimal mixed workload". Both are reported with their key metrics.
   * The latency progression sparklines should represent, on a logorithmic scale, the amount of latency increase from one streaming limit to the next, going up from the lowest limit all the way up to unlimited.
   * There should be a panel of details for this shown between the two workloads, so that a view over the different quantiles is given.
   * A similarly layed-out view should show summary latencies on the same grid pattern below it.

* All of the data use, descriptions, calculations, and results should be shared in the report.
* The workload name needs to be added to each row 

### System Performance Profile

For stage 3 report generation, each system profile will have details summarized taking data from all of the system reports.

### Cross Profile Comparisons

For stage 4 report generation, each of the system profile summary reports will be used to create a comparative study.
It should include:
* key performance indicators for each system profile
* a ranking of systems according to their results

### Documentation

A basic userguid should be created in a docs directory that is kept up-to-date with the implementation
A guide for interpreting the results should also be added there.
A README.md file needs to be created to describe the project, it's key features, and a basic example on how to run it.
The APLv2 needs to be added to this directory.

