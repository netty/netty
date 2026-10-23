# Tests and style

Authoring rules for tests (JUnit 5, AssertJ, mocking, avoiding sleeps) are in AGENTS.md under "Style and tests". Check the diff against them in addition to the items below.

## Tests
- Test assertions must use the methods from `org.junit.jupiter.api.Assertions` first, and `org.assertj.core.api.Assertions` second for more advanced assertions
- The various `assert*` methods must be imported as static imports
- Tests must always ensure that any buffers (and other resources) they allocate are released:
  - Writing a buffer to a channel implies its release when the write-future completes
  - Receiving a buffer from a channel implies taking ownership of that buffer, and it must be explicitly released
  - Channels must be closed
  - Event loop groups must be shut down
  - `ReferenceCountedOpenSslContext` instances must be released
  - Anything that implements `ReferenceCounted` must be released
  - When this is unclear, e.g. with `HttpObject` types, use `ReferenceCountUtil.safeRelease`

## Imports
- Avoid star-imports, including for static imports
- Keep import changes minimal, unless the commit is expressly about cleaning up imports
- Keep existing import order; ensure static imports always go last in their own section

## The `final` keyword
- Make new `public` classes `final` unless they need to be extended, or unless they are test classes (matching the `*Test` name pattern)
- Make variables and parameters `final` if they are referenced from lambdas or anonymous inner classes
- Make fields `final` unless they need to be mutable

## Public APIs
- Avoid adding to the public API accidentally; hide classes and methods if they are implementation details
- Public APIs must always have javadocs
