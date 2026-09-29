# Copilot instructions

## Test structure

Follow the existing nested `inner class` pattern when adding tests, especially integration tests. Group tests under `@Nested` classes by event or endpoint, then by relevant scenario (such as origin system and happy path); put shared scenario setup in the nested class's setup method and keep assertions in focused `@Test` methods.
