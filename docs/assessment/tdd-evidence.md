# TDD Evidence Log

Red-green-refactor evidence required by constitution Principle IV and the task definition of done.
Each entry records the command that was actually executed and a short summary of the actual
result, including the reason the red run failed. `Verification` tests (written after the behavior
they check) are listed separately and never presented as TDD.

**Environment**: Windows 11, JDK 21.0.12.1 (`JAVA_HOME` set per command), Maven via the
project wrapper once it exists (task T003).

| Task | Test(s) | Red run (command → observed failure) | Green run (command → result) |
|------|---------|--------------------------------------|------------------------------|
