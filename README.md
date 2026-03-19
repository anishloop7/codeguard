# CodeGuard 🛡️
### AI-Powered Code Review Assistant

> Automatically reviews GitHub Pull Requests using OpenAI GPT-4, Spring AI, and event-driven GitHub Webhooks — with configurable review profiles for Security, Performance, and General code quality.

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen?style=flat-square&logo=springboot)
![Spring AI](https://img.shields.io/badge/Spring%20AI-0.8.1-green?style=flat-square)
![OpenAI](https://img.shields.io/badge/OpenAI-GPT--4%20Turbo-blue?style=flat-square&logo=openai)
![License](https://img.shields.io/badge/license-MIT-lightgrey?style=flat-square)

---

## 📌 Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Review Profiles](#review-profiles)
- [How It Works](#how-it-works)
- [Project Structure](#project-structure)

---

## Overview

CodeGuard listens to GitHub PR events via webhooks, fetches the code diff, and runs it through a **multi-stage prompt engineering pipeline** powered by OpenAI GPT-4 Turbo. The result is a structured JSON review automatically posted back — with severity-tagged issues, line-level feedback, and a quality score.

No manual intervention needed. Open a PR → CodeGuard reviews it automatically.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         GitHub Repository                           │
│   Developer opens/updates PR  ──►  GitHub fires Webhook Event       │
└────────────────────────┬────────────────────────────────────────────┘
                         │  POST /api/webhook/github
                         │  (HMAC-SHA256 signature validated)
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      CodeGuard – Spring Boot App                    │
│                                                                     │
│  WebhookController                                                  │
│       │                                                             │
│       ▼  (async)                                                    │
│  GitHubWebhookService                                               │
│       │  resolves ReviewProfile from PR labels                      │
│       │                                                             │
│       ▼                                                             │
│  CodeReviewService  ◄──────────────────────────────────────────┐   │
│       │                                                         │   │
│       ▼                                                         │   │
│  PromptEngineService                                            │   │
│       │  1. buildSystemPrompt(profile)  → profile template     │   │
│       │  2. buildUserPrompt(diff, title, desc) → diff payload  │   │
│       │  3. Token estimation + diff truncation if needed        │   │
│       │                                                         │   │
│       ▼                                                         │   │
│  Spring AI ChatClient                                           │   │
│       │  Sends Prompt([SystemMessage, UserMessage])             │   │
│       │                                                         │   │
│       ▼                                                         │   │
│  OpenAI GPT-4 Turbo API  ───────── structured JSON ────────────┘   │
│                                                                     │
│  ReviewResultDTO  →  in-memory cache  →  REST API response          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Features

- **🔗 GitHub Webhook Integration** — Automatically triggers on `pull_request` events (`opened`, `synchronize`)
- **🔐 HMAC-SHA256 Signature Validation** — Verifies every webhook payload is genuinely from GitHub
- **🤖 Multi-Stage Prompt Engineering** — Structured system prompts + contextual user prompts for consistent LLM output
- **📋 Configurable Review Profiles** — `SECURITY`, `PERFORMANCE`, `GENERAL` via dynamic prompt templates
- **🏷️ Label-Based Profile Routing** — Add `security-review` or `performance-review` label to a PR to override the default profile
- **📊 Structured JSON Responses** — Issues with severity, file path, line number, description, and fix suggestion
- **⚡ Token Optimization** — Diff truncation, token estimation, and temperature tuning to minimize API cost
- **🔄 Fallback Handling** — Graceful degradation if the OpenAI API is unavailable
- **📡 Async Processing** — Webhooks return `200 OK` immediately; review runs in background thread pool
- **🧪 Unit Tested** — Service layer covered with Mockito-based tests

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2.4 |
| AI Integration | Spring AI 0.8.1 |
| LLM | OpenAI GPT-4 Turbo |
| Security | Spring Security (HMAC validation) |
| JSON | Jackson + JavaTimeModule |
| Async | Spring `@EnableAsync` + Thread Pool |
| Build | Maven |
| Java | 17 |
| Testing | JUnit 5, Mockito, AssertJ |

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- An [OpenAI API key](https://platform.openai.com/api-keys)
- A GitHub repository with webhook access
- A tool like [ngrok](https://ngrok.com/) for local development (to expose localhost to GitHub)

### 1. Clone the Repository

```bash
git clone https://github.com/YOUR_USERNAME/codeguard.git
cd codeguard
```

### 2. Set Environment Variables

```bash
export OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
export GITHUB_WEBHOOK_SECRET=your-random-secret-string
```

### 3. Build and Run

```bash
mvn clean install
mvn spring-boot:run
```

The server starts on `http://localhost:8080`

### 4. Expose Locally with ngrok (for development)

```bash
ngrok http 8080
```

Copy the `https://xxxx.ngrok.io` URL — you'll use this as the webhook payload URL in GitHub.

### 5. Configure GitHub Webhook

1. Go to your GitHub repo → **Settings** → **Webhooks** → **Add webhook**
2. Set **Payload URL** to `https://xxxx.ngrok.io/api/webhook/github`
3. Set **Content type** to `application/json`
4. Set **Secret** to the same value as your `GITHUB_WEBHOOK_SECRET` env variable
5. Select **Pull requests** under individual events
6. Click **Add webhook**

Now open a PR in that repo — CodeGuard will automatically review it! ✅

---

## Configuration

All config lives in `src/main/resources/application.properties`:

```properties
# OpenAI model and parameters
openai.model=gpt-4-turbo
openai.max-tokens=2048
openai.temperature=0.2

# Default review profile (GENERAL | SECURITY | PERFORMANCE)
codeguard.default-review-profile=GENERAL

# Max diff size in chars before truncation
codeguard.max-diff-chars=12000
```

---

## API Reference

### Webhook Endpoint (called by GitHub)
```
POST /api/webhook/github
Headers:
  X-GitHub-Event: pull_request
  X-Hub-Signature-256: sha256=<hmac>
Body: GitHub PR webhook payload (JSON)
```

### Manual Review Trigger
```
POST /api/review/analyze
Content-Type: application/json

{
  "owner": "myusername",
  "repositoryName": "my-repo",
  "prNumber": 42,
  "prTitle": "Add user authentication",
  "prDescription": "Implements JWT-based login flow",
  "diff": "...",
  "profile": "SECURITY"
}
```

### Fetch Cached Review Result
```
GET /api/review/{owner}/{repo}/pr/{prNumber}
```

### Sample Response
```json
{
  "prNumber": 42,
  "repositoryName": "my-repo",
  "profile": "SECURITY",
  "qualityScore": 6,
  "summary": "The PR introduces JWT authentication but has a critical secret hardcoded in the source file.",
  "issues": [
    {
      "severity": "CRITICAL",
      "filePath": "src/main/java/com/example/JwtConfig.java",
      "lineNumber": 12,
      "title": "Hardcoded JWT secret key",
      "description": "The JWT signing secret is hardcoded directly in source code, exposing it in version control.",
      "suggestion": "Move the secret to an environment variable and reference it via @Value(\"${jwt.secret}\")."
    }
  ],
  "suggestions": [
    "Token expiry is properly implemented — good practice.",
    "Consider adding refresh token support for better UX."
  ],
  "reviewedAt": "2024-11-15T14:23:01Z"
}
```

---

## Review Profiles

| Profile | Trigger Label | Focus Areas |
|---|---|---|
| `GENERAL` | *(default)* | Code quality, SOLID principles, null safety, error handling, DRY |
| `SECURITY` | `security-review` | OWASP Top 10, injection, auth flaws, hardcoded secrets, IDOR |
| `PERFORMANCE` | `performance-review` | N+1 queries, O(n²) complexity, memory leaks, missing pagination |

To activate a non-default profile, simply add the corresponding label to your PR before opening it.

---

## How It Works

### Prompt Engineering Pipeline

```
1. SYSTEM PROMPT
   ├── base-system.txt    → defines AI persona, output schema (JSON), severity levels
   └── {profile}.txt      → injects profile-specific review instructions

2. USER PROMPT
   ├── PR title + description (context)
   └── Sanitized diff (truncated to token budget)

3. LLM CALL  →  Spring AI ChatClient  →  OpenAI GPT-4 Turbo

4. RESPONSE PARSING
   ├── Strip markdown code fences (model sometimes wraps JSON)
   ├── Jackson deserialization → ReviewResultDTO
   └── Fallback to raw text if parse fails
```

### Async Webhook Flow

```
GitHub fires webhook  →  WebhookController (validates signature)
                      →  200 OK returned immediately (non-blocking)
                      →  GitHubWebhookService.processPullRequestEvent() [async thread]
                      →  Resolves ReviewProfile from PR labels
                      →  CodeReviewService.performReview()
                      →  Result cached in memory
```

---

## Project Structure

```
codeguard/
├── src/
│   ├── main/
│   │   ├── java/com/codeguard/
│   │   │   ├── CodeGuardApplication.java
│   │   │   ├── config/
│   │   │   │   ├── OpenAIConfig.java          # Spring AI bean setup
│   │   │   │   └── WebhookSecurityConfig.java # Spring Security config
│   │   │   ├── controller/
│   │   │   │   ├── WebhookController.java     # GitHub webhook entry point
│   │   │   │   └── ReviewController.java      # REST API for manual review
│   │   │   ├── service/
│   │   │   │   ├── CodeReviewService.java     # Core review pipeline
│   │   │   │   ├── PromptEngineService.java   # Prompt building + templating
│   │   │   │   └── GitHubWebhookService.java  # Webhook parsing + async dispatch
│   │   │   ├── model/
│   │   │   │   ├── ReviewRequest.java
│   │   │   │   ├── ReviewProfile.java         # GENERAL | SECURITY | PERFORMANCE
│   │   │   │   └── GitHubPullRequestEvent.java
│   │   │   ├── dto/
│   │   │   │   ├── ReviewResultDTO.java       # Structured review response
│   │   │   │   └── PullRequestDTO.java
│   │   │   └── exception/
│   │   │       ├── GlobalExceptionHandler.java
│   │   │       └── WebhookValidationException.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── prompts/
│   │           ├── base-system.txt            # Base LLM persona + JSON schema
│   │           ├── general-review.txt         # General quality profile
│   │           ├── security-review.txt        # OWASP/CVE-focused profile
│   │           └── performance-review.txt     # Perf & complexity profile
│   └── test/
│       └── java/com/codeguard/
│           ├── CodeReviewServiceTest.java
│           └── GitHubWebhookServiceTest.java
├── pom.xml
└── README.md
```

---

## License

MIT License — feel free to fork, extend, and adapt for your own projects.
