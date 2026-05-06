# Lulybi Project

Lulybi provides a unified and extensible framework for interacting with various analytical data sources. It abstracts away data platform complexities, offering a consistent API for data operations and analysis.

## Modules

*   ### `lulybi-core`
    This module is the core of Lulybi, offering a foundational, vendor-agnostic framework for data source interaction. It provides common abstractions and utilities for connecting to, querying, and processing data. `lulybi-core` is designed for extensibility, allowing easy integration with various analytical databases like **Snowflake, Amazon Redshift, Google BigQuery**, and others.

*   ### `lulybi-athena`
    This module provides specific integration and functionalities for **AWS Athena**. It leverages `lulybi-core`'s framework to enable seamless interaction with Athena's serverless interactive query service, including query execution and result processing.

## Getting Started

To set up and build the Lulybi project locally:

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/lulybi.git
    cd lulybi
    ```

2.  **Build the project:**
    Use Gradle to build all modules:
    ```bash
    ./gradlew build
    ```

## Contributing

We welcome contributions. Please refer to our [CONTRIBUTING.md](CONTRIBUTING.md) guidelines for details.

## License

This project is licensed under the [LICENSE](LICENSE).
