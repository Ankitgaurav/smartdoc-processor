# SmartDoc Processor 🚀

An AI-powered document processing backend built with **Java Spring Boot**.  
Upload documents → extract structured data using **Groq AI** → manage via REST APIs.

---

## Tech Stack

| Layer | Technology                     |
|-------|--------------------------------|
| Backend | Java 17, Spring Boot 3.x       |
| Security | Spring Security, JWT           |
| Database | PostgreSQL + Flyway migrations |
| Message Queue | Apache Kafka                   |
| Cache | Redis                          |
| Storage | AWS S3                         |
| AI Extraction | Groq AI (Llama 3.3 70B)        |
| PDF Parsing | Apache PDFBox 3.0              |
| Documentation | Swagger / OpenAPI 3            |
| Containerization | Docker + docker-compose        |

---

## Architecture

```
Client
  │
  ▼
Spring Boot App (JWT Auth)
  │
  ├── POST /upload ──→ S3 (store file)
  │                ──→ PostgreSQL (save metadata, status: PENDING)
  │                ──→ Kafka (publish processing event)
  │
  └── Kafka Consumer ──→ Redis (check cache by SHA-256 hash)
                      ──→ S3 (download file on cache MISS)
                      ──→ PDFBox (extract raw text)
                      ──→ Groq AI (extract structured JSON)
                      ──→ Redis (cache result, 24hr TTL)
                      ──→ PostgreSQL (save result, status: COMPLETED)
```

---

## Features

- **JWT Authentication** — Register, login, role-based access (USER / ADMIN)
- **S3 File Upload** — PDF, PNG, JPG with SHA-256 deduplication
- **Async Processing** — Kafka decouples upload from AI extraction
- **Redis Caching** — Same file content = instant result, zero AI API call
- **AI Extraction** — Groq Llama 3.3 extracts structured JSON from documents
- **Pagination & Filters** — Filter documents by status and date range
- **Soft Delete** — Preserves audit history, cleans S3 and Redis
- **Force Reprocess** — Evict cache and rerun AI extraction on demand
- **Swagger UI** — Interactive API documentation with JWT support
- **Global Exception Handling** — Structured error responses throughout

---

## API Endpoints

### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login — returns JWT token |

### Documents
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/documents/upload` | Upload document |
| GET | `/api/documents/{id}` | Get document by ID |
| GET | `/api/documents` | List with pagination + filters |
| GET | `/api/documents/my` | List my documents |
| GET | `/api/documents/{id}/extraction` | Get AI extracted data |
| DELETE | `/api/documents/{id}` | Soft delete document |
| POST | `/api/documents/{id}/reprocess` | Force reprocess |
| GET | `/api/documents/admin/all` | All documents (ADMIN only) |

---

## Getting Started

### Prerequisites
- Java 17
- Maven 3.9+
- PostgreSQL 16
- Redis 7
- Apache Kafka 3.x
- AWS S3 bucket
- Groq API key — free at [console.groq.com](https://console.groq.com)

### Setup

**1. Clone the repository**
```bash
git clone https://github.com/Ankitgaurav/smartdoc-processor.git
cd smartdoc-processor
```

**2. Configure application**
```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
# Fill in your DB, AWS, Groq, and JWT values
```

**3. Create PostgreSQL database**
```sql
CREATE DATABASE smartdoc_db;
```

**4. Run the application**
```bash
mvn spring-boot:run
```

**5. Open Swagger UI**
```
http://localhost:8080/swagger-ui/index.html
```

### Docker Setup
```bash
cp .env.example .env
# Fill in your secrets in .env
docker compose up --build
```

---

## Project Structure

```
src/main/java/com/smartdoc/
├── config/          # Security, Redis, OpenAPI config
├── controller/      # REST controllers
├── dto/             # Request/Response DTOs
├── entity/          # JPA entities
├── exception/       # Custom exceptions + GlobalExceptionHandler
├── kafka/           # Producer, Consumer, Message DTOs
├── repository/      # Spring Data JPA repositories
├── security/        # JWT filter, UserDetails
├── service/         # Business logic
└── util/            # File validator, hash util
```

---

## Key Design Decisions

**Why Kafka?**  
Decouples upload from AI processing. Users get instant response after upload while processing happens asynchronously.

**Why Redis?**  
SHA-256 hash of file content is used as cache key. Same document uploaded twice = cache HIT = zero AI API call = instant result.

**Why Groq instead of OpenAI?**  
Free tier (14,400 calls/day), same API format as OpenAI, near GPT-4 quality. Production swap requires only changing base URL and API key.

**Why soft delete?**  
Preserves audit history and metadata. S3 file and Redis cache are cleaned up, but DB record is retained with `deleted=true`.

---

## Author

**Ankit Gaurav**  
Java Backend Engineer  
[LinkedIn](https://linkedin.com/in/ankitgauravj) | [GitHub](https://github.com/Ankitgaurav)
