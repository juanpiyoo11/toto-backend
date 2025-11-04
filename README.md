# Toto Backend

Backend de Toto: herramienta de acompañamiento gerontológico para adultos mayores en Argentina.

## 🚀 Tech Stack

- **Framework**: Spring Boot 3.5.5
- **Language**: Java 17
- **Database**: PostgreSQL
- **Security**: Spring Security + JWT (jjwt 0.12.6, HS512)
- **ORM**: Spring Data JPA + Hibernate
- **Validation**: Jakarta Bean Validation
- **Documentation**: SpringDoc OpenAPI 3.0
- **Build Tool**: Gradle

## 📋 Prerequisites

- Java 17 or higher
- PostgreSQL 12 or higher
- Gradle 7.x or higher (or use the included Gradle wrapper)

## 🔧 Environment Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd toto-backend
   ```

2. **Create environment variables**
   
   Copy the `.env.example` file and create your own `.env` or set environment variables:
   ```bash
   cp .env.example .env
   ```

3. **Configure required environment variables**:

   | Variable | Description | Example |
   |----------|-------------|---------|
   | `JWT_SECRET` | Secret key for JWT signing (min 512 bits) | Generate: `openssl rand -base64 64` |
   | `DATABASE_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/toto` |
   | `DATABASE_USERNAME` | Database username | `toto` |
   | `DATABASE_PASSWORD` | Database password | `toto123` |
   | `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | `http://localhost:19006,http://localhost:8081,http://10.0.2.2:8081` |

4. **Set up PostgreSQL database**:
   ```sql
   CREATE DATABASE toto;
   CREATE USER toto WITH PASSWORD 'toto123';
   GRANT ALL PRIVILEGES ON DATABASE toto TO toto;
   ```

   The application will automatically create tables on startup (Hibernate DDL auto-update).

## 🏃 Running the Application

### Using Gradle Wrapper (recommended)
```bash
# Unix/macOS
./gradlew bootRun

# Windows
.\gradlew.bat bootRun
```

### Using Gradle
```bash
gradle bootRun
```

The server will start on `http://localhost:8080` (or the port specified in `PORT` environment variable).

## 📚 API Documentation

Once the application is running, access the interactive API documentation at:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

## 🔐 Authentication

The API uses JWT (JSON Web Tokens) for authentication. Most endpoints require a valid JWT token.

### Authentication Flow

1. **Register** a new user:
   ```bash
   curl -X POST http://localhost:8080/api/auth/register \
     -H "Content-Type: application/json" \
     -d '{
       "name": "Juan Pérez",
       "email": "juan@example.com",
       "password": "password123",
       "role": "ELDERLY",
       "phone": "+5491112345678"
     }'
   ```

2. **Login** to get tokens:
   ```bash
   curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{
       "email": "juan@example.com",
       "password": "password123"
     }'
   ```

   Response:
   ```json
   {
     "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
     "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
     "tokenType": "Bearer",
     "user": {
       "id": 1,
       "name": "Juan Pérez",
       "email": "juan@example.com",
       "role": "ELDERLY"
     }
   }
   ```

3. **Use the access token** in subsequent requests:
   ```bash
   curl -X GET http://localhost:8080/api/contacts?elderlyId=1 \
     -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
   ```

4. **Refresh tokens** when access token expires:
   ```bash
   curl -X POST http://localhost:8080/api/auth/refresh \
     -H "Content-Type: application/json" \
     -d '{
       "refreshToken": "YOUR_REFRESH_TOKEN"
     }'
   ```

### Token Expiry
- **Access Token**: 1 hour
- **Refresh Token**: 7 days

## 🎯 API Endpoints

### Authentication (`/api/auth`)
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/auth/register` | Register new user | No |
| POST | `/api/auth/login` | Login and get tokens | No |
| POST | `/api/auth/refresh` | Refresh access token | No |
| GET | `/api/auth/me` | Get current user info | Yes |

### Contacts (`/api/contacts`)
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/contacts?elderlyId={id}` | Get all contacts for elderly user | Yes |
| GET | `/api/contacts/{id}` | Get specific contact | Yes |
| POST | `/api/contacts` | Create new contact | Yes |
| PUT | `/api/contacts/{id}` | Update contact | Yes |
| DELETE | `/api/contacts/{id}` | Delete contact | Yes |

### Reminders (`/api/reminders`)
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/reminders?elderlyId={id}&activeOnly={bool}` | Get reminders | Yes |
| GET | `/api/reminders/{id}` | Get specific reminder | Yes |
| POST | `/api/reminders` | Create new reminder | Yes |
| PUT | `/api/reminders/{id}` | Update reminder | Yes |
| PATCH | `/api/reminders/{id}/toggle` | Toggle reminder active/inactive | Yes |
| DELETE | `/api/reminders/{id}` | Delete reminder | Yes |

### History (`/api/history`)
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/history?userId={id}&start={datetime}&end={datetime}` | Get history events | Yes |
| GET | `/api/history/{id}` | Get specific event | Yes |
| POST | `/api/history` | Create history event | Yes |

### Other Endpoints
- `/spotify/*` - Spotify integration
- `/api/whatsapp/*` - WhatsApp integration
- `/openai/*` - OpenAI integration
- `/nlu/*` - Natural Language Understanding

## 👥 User Roles

- **ELDERLY**: Adultos mayores usuarios del sistema
- **CAREGIVER**: Familiares o cuidadores

## 🗂️ Project Structure

```
src/main/java/ar/edu/uade/toto/toto_backend/
├── config/              # Configuration classes (Security, JWT, CORS)
├── dto/                 # Data Transfer Objects
│   ├── auth/           # Authentication DTOs
│   └── ...             # Other DTOs
├── entity/             # JPA entities (User, Contact, Reminder, etc.)
├── exception/          # Custom exceptions and global handler
├── repository/         # Spring Data JPA repositories
├── security/           # Security components (JWT provider, filters, principals)
├── service/            # Business logic layer
└── web/                # REST controllers
```

## 🧪 Testing

```bash
# Run all tests
./gradlew test

# Run tests with coverage
./gradlew test jacocoTestReport
```

## 🏗️ Building for Production

```bash
# Build JAR file
./gradlew build

# Run the JAR
java -jar build/libs/toto_backend-0.0.1-SNAPSHOT.jar
```

## 🐳 Docker Support (Coming Soon)

```bash
# Build image
docker build -t toto-backend .

# Run container
docker run -p 8080:8080 \
  -e JWT_SECRET=your-secret \
  -e DATABASE_URL=jdbc:postgresql://db:5432/toto \
  toto-backend
```

## 📝 Example Requests

### Create a Contact
```bash
curl -X POST http://localhost:8080/api/contacts \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "elderlyId": 1,
    "name": "Dr. García",
    "relationship": "Médico",
    "phone": "+5491156781234"
  }'
```

### Create a Reminder
```bash
curl -X POST http://localhost:8080/api/reminders \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "elderlyId": 1,
    "title": "Tomar medicación",
    "description": "Aspirina 100mg después del almuerzo",
    "reminderTime": "2024-03-20T14:00:00",
    "repeatPattern": "DAILY",
    "active": true
  }'
```

### Create a History Event
```bash
curl -X POST http://localhost:8080/api/history \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "eventType": "VOICE_COMMAND",
    "details": "Usuario solicitó reproducir música de tango"
  }'
```

## 🔒 Security Features

- JWT-based stateless authentication
- Password encryption using BCrypt
- CORS configuration for mobile apps
- Request validation with Jakarta Bean Validation
- Global exception handling
- Secure token refresh mechanism

## 🤝 Contributing

1. Create a feature branch: `git checkout -b feat/my-feature`
2. Commit your changes: `git commit -m 'feat: add some feature'`
3. Push to the branch: `git push origin feat/my-feature`
4. Submit a pull request

## 📄 License

[Add your license here]

## 👨‍💻 Authors

- Universidad Argentina de la Empresa (UADE)
- Toto Development Team
