# Idea Forge Backend (Motif Platform)

**Java Spring Boot backend API for the Motif platform** - Securely handles all business logic, AI integrations, and data operations.

## 🔒 Security Features

- ✅ **API keys stored server-side only** (no exposure to frontend)
- ✅ **JWT authentication** with Supabase tokens
- ✅ **Rate limiting** to prevent abuse
- ✅ **Input validation** on all endpoints
- ✅ **CORS configuration** for frontend security
- ✅ **PostgreSQL Row Level Security** integration

## 🚀 Quick Start

### Prerequisites

- **Java 17** or higher
- **Maven 3.6+** (included via Maven Wrapper)
- **PostgreSQL** (via Supabase)
- **Redis** (local or Upstash)
- **Groq API Key**

### 1. Clone and Setup

```bash
cd idea-forge-backend
cp .env.example .env
# Edit .env with your actual credentials
```

### 2. Configure Environment Variables

Edit `.env` file:

```env
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_DB_URL=jdbc:postgresql://db.your-project.supabase.co:5432/postgres
SUPABASE_DB_PASSWORD=your_password
SUPABASE_JWT_SECRET=your_jwt_secret
GROQ_API_KEY=gsk_your_api_key_here
```

### 3. Run with Docker (Recommended)

```bash
docker-compose up --build
```

### 4. Run Locally (Development)

```bash
# Start Redis (required)
docker run -d -p 6379:6379 --name redis redis:7-alpine

# Run application
./mvnw spring-boot:run
```

Backend will be available at: **http://localhost:8080**

Swagger UI: **http://localhost:8080/swagger-ui.html**

## 📡 API Endpoints

### AI Services

#### `POST /api/ai/analyze-idea`
Analyze a startup idea with AI

**Request:**
```json
{
  "title": "AI-powered fitness app",
  "description": "An app that creates personalized workout plans using AI",
  "targetMarket": "Fitness enthusiasts aged 25-40"
}
```

**Response:**
```json
{
  "analysisId": "uuid",
  "score": 85,
  "strengths": ["Unique value proposition", "Large market"],
  "weaknesses": ["High competition", "Technical complexity"],
  "recommendations": ["Focus on niche", "Build MVP"],
  "marketSize": "Analysis...",
  "competition": "Analysis...",
  "viability": "Analysis...",
  "timestamp": "2025-12-15T..."
}
```

#### `POST /api/ai/chat`
Chat with AI assistant

**Request:**
```json
{
  "message": "How do I validate my startup idea?",
  "history": [
    {"role": "user", "content": "Previous message"},
    {"role": "assistant", "content": "Previous response"}
  ]
}
```

**Response:**
```json
{
  "message": "To validate your startup idea...",
  "conversationId": "uuid",
  "timestamp": "2025-12-15T..."
}
```

### Authentication

All API endpoints require JWT authentication via Supabase.

**Headers:**
```
Authorization: Bearer <supabase_jwt_token>
```

## 🏗️ Project Structure

```
src/main/java/com/motif/ideaforge/
├── IdeaForgeApplication.java          # Main application
├── config/                            # Configuration
│   └── SecurityConfig.java
├── controller/                        # REST controllers
│   └── AIController.java
├── service/                           # Business logic
│   ├── ai/
│   │   ├── GroqService.java          # AI API integration
│   │   ├── IdeaAnalyzerService.java  # Idea analysis
│   │   └── ChatbotService.java       # Chatbot logic
│   └── ...
├── repository/                        # Data access
│   ├── IdeaAnalysisRepository.java
│   └── UsageLogRepository.java
├── model/                             # Data models
│   ├── entity/                        # JPA entities
│   ├── dto/                           # Request/Response DTOs
│   └── enums/
├── security/                          # Security components
│   ├── JwtAuthenticationFilter.java
│   └── SupabaseJwtValidator.java
├── exception/                         # Exception handling
│   └── GlobalExceptionHandler.java
└── middleware/                        # Middleware components
```

## 🔧 Configuration

### Rate Limiting

Configure in `application.yml`:

```yaml
app:
  rate-limit:
    enabled: true
    ai-analyze: 10      # per hour
    ai-generate: 5      # per hour
    ai-chat: 50         # per hour
```

### CORS

Configure allowed origins:

```yaml
app:
  cors:
    allowed-origins: http://localhost:5173,https://your-frontend.vercel.app
```

## 🧪 Testing

```bash
# Run all tests
./mvnw test

# Run with coverage
./mvnw test jacoco:report

# Integration tests only
./mvnw verify -P integration-tests
```

## 📦 Deployment

### Railway

1. Connect GitHub repository to Railway
2. Add environment variables in Railway dashboard
3. Railway auto-detects Dockerfile and deploys
4. Get deployment URL: `https://your-app.up.railway.app`

### Render

1. Create new Web Service in Render
2. Connect GitHub repository
3. Set Docker environment
4. Add environment variables
5. Deploy

### Heroku

```bash
heroku create idea-forge-backend
heroku addons:create heroku-redis:mini
heroku config:set GROQ_API_KEY=your_key_here
git push heroku main
```

## 🔐 Security Checklist

Before deploying to production:

- [ ] All API keys in environment variables
- [ ] HTTPS enforced
- [ ] CORS configured for production frontend URL
- [ ] Rate limiting enabled
- [ ] Supabase RLS policies enabled
- [ ] No secrets in Git history
- [ ] Swagger UI disabled in production
- [ ] Error messages don't expose sensitive data
- [ ] Logging configured properly

## 📊 Monitoring

Access health endpoint:
```bash
curl http://localhost:8080/actuator/health
```

View metrics:
```bash
curl http://localhost:8080/actuator/metrics
```

## 🐛 Troubleshooting

### Database Connection Issues

```bash
# Check database URL format
jdbc:postgresql://db.xxxxx.supabase.co:5432/postgres

# Test connection
psql "postgresql://postgres:[PASSWORD]@db.xxxxx.supabase.co:5432/postgres"
```

### JWT Validation Errors

- Ensure `SUPABASE_JWT_SECRET` matches your Supabase project
- Check token hasn't expired
- Verify Authorization header format: `Bearer <token>`

### Redis Connection Issues

```bash
# Test Redis connection
redis-cli -h localhost -p 6379 ping
```

## 📝 Next Steps

1. **Database Migration**: Create `ai_usage_logs` table (see plan for SQL)
2. **Test API**: Use Postman or Swagger UI
3. **Update Frontend**: Integrate frontend with backend API
4. **Deploy**: Choose hosting provider and deploy
5. **Monitor**: Set up logging and monitoring

## 🤝 Contributing

See main repository for contribution guidelines.

## 📄 License

See main repository for license information.

## 📞 Support

For issues or questions, please open a GitHub issue.
