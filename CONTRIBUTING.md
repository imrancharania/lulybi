# Contributing to Lulybi

We welcome contributions to the Lulybi project! Your help is invaluable in making this project better. This document outlines guidelines for contributing to Lulybi.

## How Can I Contribute?

### Reporting Bugs

*   If you find a bug, please open an issue on our GitHub repository.
*   Clearly describe the bug, including steps to reproduce it, expected behavior, and actual behavior.
*   Include any relevant error messages or stack traces.
*   Mention your operating system, Java version, and any other relevant environment details.

### Suggesting Enhancements

*   If you have an idea for a new feature or an improvement to an existing one, please open an issue.
*   Clearly describe the enhancement, including its purpose and potential benefits.
*   Provide examples or use cases where applicable.

### Contributing Code

1.  **Fork the repository:** Start by forking the `lulybi` repository to your GitHub account.
2.  **Clone your fork:**
    ```bash
    git clone https://github.com/your-username/lulybi.git
    cd lulybi
    ```
    (Replace `your-username` with your GitHub username.)
3.  **Create a new branch:**
    ```bash
    git checkout -b feature/your-feature-name
    ```
    or
    ```bash
    git checkout -b bugfix/issue-number
    ```
4.  **Make your changes:** Implement your feature or bug fix.
5.  **Write tests:** Ensure your changes are covered by appropriate unit and/or integration tests.
6.  **Run tests:** Before submitting, make sure all existing tests pass and your new tests pass.
    ```bash
    ./gradlew clean build
    ```
7.  **Format your code:** Adhere to the project's code style. We use [Ktlint](https://ktlint.github.io/) for Kotlin code. You can run `./gradlew ktlintFormat` to automatically format your code.
8.  **Commit your changes:** Write clear, concise commit messages.
    ```bash
    git commit -m "feat: Add new feature for X"
    ```
    or
    ```bash
    git commit -m "fix: Resolve issue #123 - description of fix"
    ```
9.  **Push to your fork:**
    ```bash
    git push origin feature/your-feature-name
    ```
10. **Open a Pull Request (PR):**
    *   Go to the original `lulybi` repository on GitHub.
    *   You should see an option to create a new pull request from your recently pushed branch.
    *   Provide a clear title and description for your PR, explaining the changes and why they are necessary.
    *   Reference any related issues (e.g., `Closes #123`).

## Code Style

*   We follow standard Kotlin coding conventions.
*   Please ensure your code is formatted using `ktlint`. You can run `./gradlew ktlintFormat` to apply formatting automatically.

## Testing

*   All new features and bug fixes should be accompanied by appropriate tests.
*   Ensure that existing tests pass after your changes.

## License

By contributing to Lulybi, you agree that your contributions will be licensed under the project's [LICENSE](LICENSE).

Thank you for contributing to Lulybi!
