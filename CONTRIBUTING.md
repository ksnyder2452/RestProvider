# Contributing to RestProvider

Thank you for your interest in contributing to RestProvider! This document provides guidelines and instructions for contributing to the project.

## Code of Conduct

Please be respectful and constructive in all interactions with other contributors and maintainers.

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/RestProvider.git
   cd RestProvider
   ```
3. **Create a new branch** for your changes:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## Development Setup

RestProvider is primarily a Java project. Ensure you have the following installed:

- Java Development Kit (JDK) 11 or higher
- Maven or Gradle (as per the project's build configuration)
- Git

### Building the Project

```bash
# Build the project
mvn clean install

# Or with Gradle
gradle build
```

## Making Changes

### Code Style

- Follow Java naming conventions and best practices
- Maintain consistent indentation and formatting
- Write clear, descriptive variable and method names
- Add comments for complex logic

### Commit Messages

- Use clear, concise commit messages
- Start with a verb (Add, Fix, Update, Remove, etc.)
- Reference relevant issues or PRs when applicable
- Example: `Add support for OAuth2 authentication in REST provider`

### Testing

- Write tests for new functionality
- Ensure all existing tests pass before submitting
- Run tests locally:
  ```bash
  mvn test
  ```

## Submitting Changes

1. **Push your branch** to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Create a Pull Request** on GitHub with:
   - A clear, descriptive title
   - A description of the changes and their purpose
   - Reference to any related issues (use `Closes #123`)
   - Screenshots or examples if applicable

3. **Address feedback** from code reviews promptly

## Reporting Issues

When reporting bugs or suggesting features:

- **Check existing issues** first to avoid duplicates
- **Provide clear descriptions** of the problem or feature
- **Include steps to reproduce** for bugs
- **Share relevant logs or error messages**
- **Mention your environment** (OS, Java version, etc.)

## Project Structure

RestProvider is a multi-integration automation API server for test orchestration and utility workflows, written primarily in Java (98.5%).

Key areas for contribution:
- API endpoints and integrations
- Test orchestration logic
- Utility workflows
- Documentation
- Bug fixes and performance improvements

## Questions?

Feel free to open an issue for questions or discussions about the project. We're here to help!

## License

By contributing to RestProvider, you agree that your contributions will be licensed under the same license as the project.

---

Thank you for contributing to RestProvider! 🎉
