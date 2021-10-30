# Contributing to cache2k

cache2k is released under the Apache 2.0 license. If you would like to contribute
something, or want to hack on the code this document should help you get started.

## Code of Conduct

This project adheres to the [Contributor Covenant code of conduct](CODE_OF_CONDUCT.md).
By participating, you are expected to uphold this code.

## Help, Please!

There are many ways to help an Open Source project, besides improving the code base.
Here are some ideas:

- Review and improve the project documentation
- Write a blog article
- If you are happy about cache2k or it solved a specific problem, please consider
  giving feedback at [Happy Users Feedback issue](https://github.com/cache2k/cache2k/issues/139)
- Cache2k has many features, sometimes we like to change or drop a feature and don't know
  exactly which feature users depend upon. Please consider giving feedback on how
  you use cache2k and which features are helpful for you at:
  [Happy Users Feedback issue](https://github.com/cache2k/cache2k/issues/139)
- Post usage questions on Stackoverflow. This helps other users and also promotes the project
- Test `.Alpha` and `.Beta` releases early with your code base and submit bug reports

## Usage Questions

If you have a general usage question please ask on [Stack Overflow](https://stackoverflow.com) 
and use the tag `cache2k`.

If you find an error in the code documentation or user guide, please consider submitting
a pull request with a correction.

## Using GitHub Issues

We use GitHub issues to track bugs and enhancements. If you are reporting a bug, 
please help to speed up problem diagnosis by providing as much information as possible. 
Ideally, that would include a small sample project that reproduces the problem.

## Pull Requests

Pull requests are very welcome. Please start with small and simple pull requests.
Submitting a pull request requires to sign the 
[Developer Certificate of Origin](https://developercertificate.org/).

Before submitting a heavy, non-trivial or complex pull request, please open an issue
first and seek feedback from other users and contributors early on.

Here are a few things you can do that will increase the likelihood of your pull request being accepted:

- Write and update tests.
- Keep your change as focused as possible. If there are multiple changes you would like to make 
  that are not dependent upon each other, consider submitting them as separate pull requests.
- Write a good [commit message](https://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html)

Before submitting a pull request, please check whether all tests pass, via:

````
mvn verify
````

## Development Requirements

The project runs on Java 8 and requires Java 11 to compile. The used maven version 
should be 3.6.3 or above. The project can be imported in IntelliJ or Eclipse by doing
a standard maven project import.
