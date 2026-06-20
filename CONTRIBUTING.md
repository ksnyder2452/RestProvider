# Contributing to RestProvider

Thank you for your interest in contributing to RestProvider! This document explains how to set up the project, follow coding standards, and submit high-quality pull requests.

## Code of Conduct

Please be respectful, collaborative, and constructive in all project interactions.

## Getting Started

1. **Fork the repository** on GitHub.
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/RestProvider.git
   cd RestProvider
   ```
3. **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## Development Environment

RestProvider is primarily a **Java** project and uses **Maven** for builds.

### Requirements

- JDK 11 or newer
- Maven 3.8+
- Git

Verify tools:

```bash
java -version
mvn -version
git --version
```

## Build and Run (Maven)

Use Maven commands from the repository root:

```bash
# Clean and compile
mvn clean compile

# Run unit/integration tests
mvn test

# Build distributable artifacts
mvn clean package

# Full verification lifecycle
mvn clean verify

# Install artifact to local Maven repository
mvn clean install
```

## Code Style Guidelines

- Follow standard Java naming conventions.
- Keep methods focused and readable.
- Prefer clear names over abbreviations.
- Add comments only where intent is not obvious from code.
- Keep changes scoped to the PR objective.

## Testing Expectations

Before opening a pull request:

- Add or update tests for behavior changes.
- Ensure all tests pass locally:
  ```bash
  mvn test
  ```
- Run full verification for larger or riskier changes:
  ```bash
  mvn clean verify
  ```

## Commit Message Guidance

Use clear, imperative commit messages:

- `Add request timeout handling to orchestration endpoint`
- `Fix null pointer in integration status mapper`
- `Update API docs for utility workflow parameters`

When relevant, reference issues in commit messages or PR descriptions (e.g., `Closes #123`).

## Submitting a Pull Request

1. Push your branch:
   ```bash
   git push origin feature/your-feature-name
   ```
2. Open a pull request against `main`.
3. Fill out the PR template completely.
4. Ensure CI checks pass.
5. Respond to review feedback promptly.

## Reporting Bugs / Requesting Features

Please open an issue and include:

- Clear summary
- Steps to reproduce (for bugs)
- Expected vs actual behavior
- Relevant logs or stack traces
- Environment details (OS, JDK, Maven version)

## Project Contribution Areas

Common contribution areas include:

- API endpoint enhancements
- Integration reliability improvements
- Test orchestration features
- Utility workflow improvements
- Documentation and examples
- Bug fixes and performance tuning

## License

By contributing, you agree your contributions are licensed under the project’s existing license.

---

Thanks for helping improve RestProvider! 🎉
