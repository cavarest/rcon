# Contributing to Rcon

Thank you for your interest in contributing to Rcon! This document provides guidelines for contributing to the project.

## Getting Started

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/rcon.git
   cd rcon
   ```
3. Add the upstream remote:
   ```bash
   git remote add upstream https://github.com/cavarest/rcon.git
   ```

## Development

### Building

```bash
./gradlew build
```

### Running Tests

```bash
# Unit tests only (fast, no Docker)
./gradlew test

# Integration tests (requires Docker daemon)
./gradlew integrationTest

# All tests
./gradlew test integrationTest
```

**Note**: Integration tests run against a real Minecraft Paper server using Docker. They can take 10+ minutes due to container overhead.

### Skip Integration Tests Locally

```bash
SKIP_INTEGRATION_TESTS=true ./gradlew test
```

## Code Style

- Follow existing code style and conventions
- Use meaningful variable and method names
- Add Javadoc to public APIs
- Keep methods focused and concise

## Documentation

Documentation is in the `docs/` directory using Jekyll and AsciiDoc:

```bash
cd docs
bundle install
bundle exec jekyll serve
```

Visit http://localhost:4000 to preview changes.

## Commit Messages

Use semantic commit messages:

```
<type>(<scope>): <subject>
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Example: `fix(auth): resolve login bug`

## Pull Requests

1. Create a feature branch from `main`
2. Make your changes with clear commit messages
3. Ensure tests pass
4. Push to your fork and create a pull request

## Testing Your Changes

Before submitting a PR, please:

1. Run all tests: `./gradlew test integrationTest`
2. Build documentation: `cd docs && bundle exec jekyll build`
3. Check links: `cd docs && bundle exec lychee . --config .lychee.toml`

## Questions?

Feel free to open an issue for questions or discussion.
