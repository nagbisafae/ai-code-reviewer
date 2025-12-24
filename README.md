# AI Code Reviewer

An automated code review bot that integrates with GitHub to provide AI-powered feedback on pull requests using Google Gemini.

## Features

- GitHub App integration with JWT authentication
- AI-powered code analysis using Google Gemini 2.0 Flash
- Line-level inline comments on specific code issues
- Contextual filtering - reviews only changed code
- Asynchronous processing for scalability
- Official GitHub bot badge

## Architecture

```
GitHub PR → Webhook → Spring Boot → Google Gemini → Inline Comments
```

**Components:**
- `WebhookController` - Receives GitHub events
- `ReviewOrchestrator` - Coordinates workflow
- `GitHubAppAuthService` - JWT authentication
- `AIReviewService` - Google Gemini integration
- `GitHubCommentService` - Posts feedback

## Prerequisites

- Java 17+
- Gradle 8+
- **GitHub App** (for receiving webhooks and posting comments)
- **Google Cloud Project** with Gemini API enabled (for AI code analysis)
- ngrok (for local development)

## Installation

1. Clone the repository
```bash
git clone https://github.com/nagbisafae/ai-code-reviewer.git
cd ai-code-reviewer
```

2. Configure `application.properties`

**Note:** This project uses two services:
- **GitHub** - for pull request integration and webhook events
- **Google Gemini** - for AI-powered code analysis

```properties
# Google Cloud (for AI analysis)
spring.ai.google.genai.project-id=your-gcp-project-id
spring.ai.google.genai.location=us-central1

# GitHub App (for PR integration)
github.app.id=your-github-app-id
github.app.private-key-path=github-app-private-key.pem
```

3. Build and run
```bash
./gradlew build
./gradlew bootRun
```

4. Expose with ngrok
```bash
ngrok http 8080
```

5. Configure GitHub App webhook URL: `https://your-ngrok-url/webhook/github`

## GitHub App Setup

1. Create app at https://github.com/settings/apps
2. Permissions: Contents (Read), Pull requests (Read & Write)
3. Webhook events: Pull requests (opened, synchronized)
4. Download private key
5. Install on target repositories

## Google Cloud Setup

1. Create project at https://console.cloud.google.com
2. Enable Gemini API (Generative Language API)
3. Note your project ID
4. Ensure billing is enabled (Gemini API requires it)

## Technology Stack

- Spring Boot 3.5.8
- Spring AI 1.1.1
- Google Gemini 2.0 Flash
  
## Limitations

- Java files only
- Cloud deployment issues on some platforms (GCP Cloud Run, Railway)
- Single-threaded processing
- Requires Google Cloud API access

## Future Work

- Multi-language support (Python, JavaScript, TypeScript)
- Alternative deployment platforms
- Scalability improvements with message queues
- Configuration dashboard
- GitLab/Bitbucket support

## License

MIT License

## Author

Safae NAGBI  

## Contact

For questions, open an issue on GitHub.
