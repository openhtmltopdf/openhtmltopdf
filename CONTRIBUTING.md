# Contributing to openhtmltopdf

Thanks for helping out. Bug reports, test cases and pull requests are all
welcome.

## Pull requests

- Branch off `main` and open the pull request against `main`.
- Run the test suite locally before pushing: `mvn test`.
- Include a test for the behaviour you changed. Rendering changes usually
  belong in `openhtmltopdf-examples` — see the visual and non-visual
  regression tests there for the pattern.
- Keep the change focused. A pull request that fixes one thing is reviewed and
  merged far faster than one that fixes three.

Every merge to `main` publishes a release, so fixes and their tests should land
together in a single pull request rather than as follow-ups.

## AI-assisted contributions

We accept pull requests written with AI assistance, and we use it ourselves.
We ask two things in the pull request description: say that AI was involved,
and say whether we may push fixes directly to your branch.

Neither is a restriction. It changes how we review: for an AI-assisted change
where we may push, we tend to correct small things ourselves and merge, instead
of opening a review round that has to travel back through a code generator.

If you use AI, please still:

- read and understand the diff you are submitting;
- run the test suite locally (`mvn test`);
- include a test for the behaviour you changed;
- be around to answer questions about the change.

That last point is the one that matters most. The usual problem with an
AI-generated pull request is not the code — it is an author who cannot say why
it works or confirm that it does.

## Reporting bugs

A self-contained HTML file that reproduces the problem is worth more than any
description of it. Please say which version of openhtmltopdf you are using and
what you expected the output to look like.
