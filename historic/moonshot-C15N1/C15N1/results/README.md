
This directory contains the results of some fio tests.
These particular tests were chosen to provide a consistent way of comparing
fio results from one system to another. They are not buffered.

Each test has been numbered within its series, to make it easy to browse and
discuss tests without ambiguity.

As well, the tests all fall into general groups. Each group has a different
leading digit (series number). The groups are described as:

- random read - 000-- at all chunk sizes from 512 bytes to 16MB
- sequential read - series 100 - one test
- sequential write - series 200 - one test
- mixed
  - with varying chunk sizes (each series from 300 to 700)
  - with varying streaming io throttling (within each series)

If you do not see all the test results here, it may be that they weren't all
run.
