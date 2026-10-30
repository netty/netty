# Netty AI agent guidance

## Working agreement

- If the user asks how to contribute or submit their change, mention that contributions require signing the CLA, and point them to https://netty.io/wiki/developer-guide.html for environment setup.
- The agent may only draft trivial PR descriptions, issue comments or review replies. Discussion with maintainers should be in the contributor's own words.
- Issue and pull request descriptions must follow their respective templates in `.github`.
- Changes that are potentially breaking - e.g. adding validation by default, or changes to public API - must be called out in commit messages and PR descriptions.
- Before handing work back, say what you changed and what you verified, so the human can review it properly.

## Branches and Java baselines

| Branch | Role | Java baseline |
| ------ | ---- | ------------- |
| 4.1    | maintenance, bug fixes only | Java 6 |
| 4.2    | default branch, most development | Java 8 |
| 5.0    | next major | Java 25 |

- Check which branch you are on before using any language feature or JDK API.
- Only bug fixes are backported from 4.2 to 4.1.
- Changes that are not specific to one branch are also ported to 5.0.
- Pull requests are usually merged with squash-rebase, so porting is a cherry-pick of a single commit.
- Porting is automatic via PR labels (`needs-cherry-pick-<branch>`, see `.github/workflows/autoport-*.yml`). When a cherry-pick conflicts, the port must be done manually.

## Security

Netty parses untrusted network input (HTTP/1.x, HTTP/2, HTTP/3, and more), implements TLS on top of BoringSSL/OpenSSL, and contains C code.
Treat every change as security-sensitive.

- Validate lengths, counts and offsets that come off the wire. Watch for integer overflow, unbounded allocation and unbounded loops.
- Be careful with reference counting and buffer indices in C and Java code alike.
- Do not weaken or bypass existing validation to make a test pass.
- Refer to applicable RFCs for guidance, and precise parsing/validation rules.
- The Netty Threat Model is available at https://github.com/netty/netty/wiki/Threat-model
- The Netty Security Policy (how to report security issues) is available at https://github.com/netty/.github/blob/main/SECURITY.md

## Build and verify

Do not reimplement CI checks by hand. Run the right build command instead. Environment setup (JDKs, native toolchains) is covered in https://netty.io/wiki/developer-guide.html

- Some modules need .jar files from other modules in order to compile. Prime the local build cache with: `./mvnw install -DskipTests -Pfast -T1C`
- Compiling a single module or running a specific test can be done with standard maven commands.
- Run checkstyle and revapi checks with: `./mvnw verify -DskipTests`
- If a new non-test dependency is added, the `NOTICE.txt` file must be updated with their license notice.
- Check for buffer leaks with `./mvnw <command> -Pleak -Dio.netty.allocator.type=pooled > target/build.log` and the `.github/scripts/check_leak.sh <logfile>` script.

## Style and tests

- New files must have license headers with the year the file was introduced.
- A bug fix comes with a regression test.
- Non-trivial performance fixes (complex code or more than 20 lines changed) must demonstrate their worth with a JMH benchmark in `microbench`.
- Use JUnit 5 and AssertJ for writing tests.
- Don't use mocking unless existing tests for the class already do so, or it would introduce _significant_ complexity to avoid.
- Avoid sleeps in tests if at all possible, avoid sizing test resources off `NettyRuntime.availableProcessors()`, and avoid asserting on elapsed time.
