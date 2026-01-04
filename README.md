# AuthX - Multi-Factor Authentication System

A production-grade Multi-Factor Authentication system built with Spring Boot, featuring WebAuthn (FIDO2) biometric authentication, OTP fallback, and comprehensive security features.

## 🚀 Features

- **Multi-Factor Authentication**: WebAuthn (FIDO2) + OTP fallback
- **JWT-based Authentication**: Stateless with refresh token support
- **Role-Based Access Control (RBAC)**: Flexible permission system
- **Security Hardening**: Rate limiting, account lockout, audit logging
- **Redis Integration**: Session management and OTP storage
- **PostgreSQL**: Primary data persistence
- **Cloud-Ready**: Environment-based configuration for Render, Vercel, and Neon.tech deployment

## 🏗️ Architecture

```
├── controller/     # REST API endpoints
├── service/        # Business logic layer
├── repository/     # Data access layer
├── model/          # JPA entities
├── dto/            # Data transfer objects
├── config/         # Configuration classes
├── security/       # Security components
└── util/           # Utility classes
```

## 🛠️ Tech Stack

- **Java 17**
- **Spring Boot 3.2**
- **Spring Security**
- **PostgreSQL** (Primary database)
- **Redis** (Session & OTP storage)
- **WebAuthn** (FIDO2 authentication)
- **JWT** (JSON Web Tokens)
- **Maven** (Build tool)
- **Docker** (Containerization)

## 🚀 Quick Start

### Prerequisites
- **Docker** and **Docker Compose** (for PostgreSQL and Redis)
- **Java 17+** (for Spring Boot backend)
- **Maven** (included via `mvnw` wrapper)
- **Python 3** (for frontend development server)

### 1. Clone and Setup
```bash
# Clone the repository
git clone <your-repo-url>
cd authx

# Copy environment variables template
cp env.example .env

# Edit .env with your local configuration (optional for basic setup)
# The defaults work for local development
```

### 2. Start Infrastructure (PostgreSQL & Redis)
```bash
# Start database and cache using Docker Compose
docker-compose up -d postgres redis

# Verify services are running
docker-compose ps

# Check logs if needed
docker-compose logs postgres
docker-compose logs redis
```

### 3. Start Backend
```bash
# Option 1: Run with Maven wrapper (recommended)
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=dev"

# Option 2: Build and run JAR
./mvnw clean package
java -jar target/authx-mfa-system-1.0.0.jar --spring.profiles.active=dev

# Option 3: Run with environment variables
export SPRING_PROFILE=dev
./mvnw spring-boot:run
```

### 4. Start Frontend
```bash
# In another terminal
cd frontend
python3 server.py 3000

# Or specify a different port
python3 server.py 8000
```

### Access Your Application
- **🌐 Web Interface**: http://localhost:3000
- **📚 API Documentation**: http://localhost:8080/swagger-ui.html
- **📖 OpenAPI Spec**: http://localhost:8080/api-docs
- **❤️ Health Check**: http://localhost:8080/api/v1/actuator/health
- **🔧 Actuator Metrics**: http://localhost:8080/api/v1/actuator/metrics

### Stop Services
```bash
# Stop backend (Ctrl+C in terminal)

# Stop frontend (Ctrl+C in terminal)

# Stop infrastructure
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

## 📋 API Endpoints

### Authentication
- `POST /auth/register` - User registration
- `POST /auth/login` - User login
- `POST /auth/mfa/verify` - MFA verification
- `POST /auth/refresh` - Refresh access token
- `POST /auth/logout` - User logout

### MFA Management
- `POST /mfa/setup/webauthn` - Setup WebAuthn
- `POST /mfa/setup/otp` - Setup OTP
- `GET /mfa/methods` - Get available MFA methods

### User Management
- `GET /users/profile` - Get user profile
- `PUT /users/profile` - Update user profile
- `DELETE /users/profile` - Delete user profile
- `GET /users/audit-logs` - Get user audit logs

## 🔧 Configuration

### Environment Variables

### Email Service Configuration

You can use any of these email services:

#### 1. Gmail SMTP (Free - Recommended for development)
```bash
export MAIL_HOST=smtp.gmail.com
export MAIL_PORT=587
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-app-password  # Use App Password, not regular password
```

#### 2. SendGrid
```bash
export MAIL_HOST=smtp.sendgrid.net
export MAIL_PORT=587
export MAIL_USERNAME=apikey
export MAIL_PASSWORD=your-sendgrid-api-key
```

#### 3. Mailgun
```bash
export MAIL_HOST=smtp.mailgun.org
export MAIL_PORT=587
export MAIL_USERNAME=postmaster@your-domain.mailgun.org
export MAIL_PASSWORD=your-mailgun-password
```

#### 4. Amazon SES
```bash
export MAIL_HOST=email-smtp.us-east-1.amazonaws.com
export MAIL_PORT=587
export MAIL_USERNAME=your-aws-access-key
export MAIL_PASSWORD=your-aws-secret-key
```

#### 5. Resend
```bash
export MAIL_HOST=smtp.resend.com
export MAIL_PORT=587
export MAIL_USERNAME=resend
export MAIL_PASSWORD=your-resend-api-key
```

## Environment Variables

Key environment variables (see `.env.example`):

```bash
# Database
DB_HOST=localhost
DB_NAME=authx
DB_USERNAME=authx_user
DB_PASSWORD=authx_pass

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT
JWT_SECRET=your-secret-key
JWT_EXPIRATION=86400000

# MFA
WEBAUTHN_RP_ID=localhost
WEBAUTHN_ORIGIN=http://localhost:8080
```

### Application Profiles

- **dev**: Development with detailed logging
- **prod**: Production with optimized settings

## 🧪 Testing

### Backend Testing

#### Unit Tests
```bash
# Run all unit tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=JwtUtilTest

# Run with coverage (if configured)
./mvnw test jacoco:report
```

#### Integration Tests
```bash
# Run integration tests
./mvnw test -Dtest=**/*IntegrationTest

# Run specific integration test
./mvnw test -Dtest=AuthIntegrationTest
```

#### Manual API Testing

**1. Register a new user:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "Test123!@#"
  }'
```

**2. Login:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test123!@#"
  }'
```

**3. Check health:**
```bash
curl http://localhost:8080/api/v1/actuator/health
```

**4. View API documentation:**
- Open http://localhost:8080/swagger-ui.html in your browser
- Interactive API testing available

### Development & Test Endpoints

The application includes several development endpoints for testing:

#### Test Controllers (Available in all profiles)

**1. Config Test Controller** (`/api/v1/test/config`)
- **GET** `/api/v1/test/config` - Check email configuration status
- Useful for verifying email service setup

**2. Email Test Controller** (`/api/v1/test/email-*`)
- **POST** `/api/v1/test/email-connection` - Test email connection
- **POST** `/api/v1/test/send-test-email?to=your@email.com` - Send test email
- Useful for testing email service configuration

#### Dev Controller (Only when `sms.provider=mock`)

**3. Dev Controller** (`/api/v1/dev/sms/*`)
- **GET** `/api/v1/dev/sms/{phoneNumber}?minutes=10` - Get mock SMS messages
- **GET** `/api/v1/dev/sms/all?minutes=10` - Get all recent mock SMS
- **POST** `/api/v1/dev/sms/test` - Send test SMS
- **GET** `/api/v1/dev/sms/test-numbers` - Get test phone numbers
- **DELETE** `/api/v1/dev/sms/clear/{phoneNumber}` - Clear mock SMS

**Note:** Dev controller is only available when `sms.provider=mock` in your configuration.

### Frontend Testing

#### Manual Testing Checklist

1. **User Registration**
   - Open http://localhost:3000
   - Click "Create Account"
   - Fill in registration form
   - Verify email validation
   - Verify password strength requirements

2. **User Login**
   - Login with registered credentials
   - Test "Remember Me" functionality
   - Test error handling for invalid credentials

3. **MFA Setup**
   - Enable MFA from dashboard
   - Setup WebAuthn (biometric authentication)
   - Setup Email OTP
   - Test preferred method selection

4. **MFA Authentication**
   - Logout and login again
   - Complete MFA verification
   - Test different MFA methods

5. **Dashboard Features**
   - View user profile
   - Change password
   - View audit logs
   - Manage WebAuthn credentials

6. **Admin Features** (if admin user)
   - Access admin dashboard
   - View all users
   - Manage user roles
   - View system audit logs

#### Browser Console Testing
```javascript
// Enable debug mode
localStorage.setItem('authx_debug', 'true');

// Check stored tokens
console.log(localStorage.getItem('authx_access_token'));

// Clear all auth data
localStorage.clear();
```

### Testing with Postman/Thunder Client

1. Import the OpenAPI spec from http://localhost:8080/api-docs
2. Use the Swagger UI at http://localhost:8080/swagger-ui.html
3. Test endpoints interactively with authentication tokens

## 🔒 Security Features

### Authentication Security
- BCrypt password hashing
- JWT with secure signing
- Refresh token rotation
- Account lockout after failed attempts

### MFA Security
- WebAuthn (FIDO2) biometric authentication
- Time-based OTP with Redis TTL
- MFA method enrollment and management
- Fallback authentication methods

### API Security
- Rate limiting per endpoint
- CORS configuration
- CSRF protection
- XSS protection headers
- Request/response logging

### Audit & Monitoring
- Comprehensive audit logging
- Failed login attempt tracking
- Security event monitoring
- User activity tracking

## 🚀 Deployment

This project is configured for deployment on:
- **Backend**: Render.com
- **Frontend**: Vercel
- **Database**: Neon.tech (PostgreSQL)
- **Cache**: Render Redis or External Redis (Upstash, Redis Cloud, etc.)

### Prerequisites

1. **Neon.tech Database Setup**:
   - Create a free account at [neon.tech](https://neon.tech)
   - Create a new PostgreSQL database
   - Copy the connection string (DATABASE_URL)

2. **Render Account**:
   - Create a free account at [render.com](https://render.com)
   - Create a new Web Service for the backend
   - Optionally create a Redis instance (or use external Redis)

3. **Vercel Account**:
   - Create a free account at [vercel.com](https://vercel.com)

### Backend Deployment (Render)

**Option 1: Using Render Blueprint (Recommended)**

1. **Deploy using render.yaml**:
   - Push your code to GitHub
   - In Render dashboard, click "New" → "Blueprint"
   - Connect your repository
   - Render will automatically detect `render.yaml` and create the service
   - Fill in the environment variables marked as `sync: false` in the dashboard

**Option 2: Manual Deployment**

1. **Prepare the application**:
```bash
./mvnw clean package
```

2. **Deploy to Render**:
   - Connect your GitHub repository to Render
   - Create a new **Web Service**
   - Build Command: `./mvnw clean package -DskipTests`
   - Start Command: `java -Dserver.port=$PORT -Dspring.profiles.active=render -jar target/authx-mfa-system-1.0.0.jar`
   - Or use the `Procfile` (Render will detect it automatically)

3. **Configure Environment Variables in Render**:
```bash
# Spring Profile
SPRING_PROFILE=render

# Database (Neon.tech)
DATABASE_URL=postgresql://user:password@ep-xxx.region.aws.neon.tech/dbname?sslmode=require

# Redis (Render Redis or External)
REDIS_URL=redis://:password@host:port

# JWT (Generate a strong random secret)
JWT_SECRET=your-production-secret-key-min-32-characters-long
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000

# MFA Configuration
WEBAUTHN_RP_ID=your-domain.com
WEBAUTHN_RP_NAME=AuthX MFA System
WEBAUTHN_ORIGIN=https://your-backend.onrender.com

# Email Configuration
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-gmail-app-password

# Optional
DDL_AUTO=update
```

4. **Deploy**:
   - Render will automatically build and deploy on every push to your main branch
   - Note your backend URL: `https://your-app-name.onrender.com`

### Frontend Deployment (Vercel)

1. **Update Frontend Configuration**:
   - Edit `frontend/js/config.js`
   - Update `API_BASE_URL` with your Render backend URL:
   ```javascript
   return window.BACKEND_URL || 'https://your-app-name.onrender.com/api/v1';
   ```

2. **Deploy to Vercel**:
   ```bash
   cd frontend
   vercel
   ```
   Or connect your GitHub repository to Vercel and deploy the `frontend` directory

3. **Configure Environment Variables (if needed)**:
   - In Vercel dashboard, you can set `BACKEND_URL` if you want to override the default

4. **Update WebAuthn Configuration**:
   - After deployment, update `WEBAUTHN_RP_ID` and `WEBAUTHN_ORIGIN` in Render to match your Vercel domain

### Post-Deployment Checklist

- [ ] Backend is accessible at `https://your-app.onrender.com/api/v1/actuator/health`
- [ ] Frontend is accessible at `https://your-app.vercel.app`
- [ ] Database connection is working (check Render logs)
- [ ] Redis connection is working (check Render logs)
- [ ] WebAuthn origin is correctly configured
- [ ] CORS is properly configured for your frontend domain
- [ ] Email service is configured and working

### Docker Deployment (Local Development)

The `docker-compose.yml` file is configured for local development infrastructure only (PostgreSQL and Redis). The Spring Boot application runs directly on your host machine.

**For production Docker deployment**, you would need to:
1. Create a `Dockerfile` for the Spring Boot application
2. Add the app service to `docker-compose.yml`
3. Configure networking between services

**Current setup (local development):**
```bash
# Start only infrastructure (PostgreSQL & Redis)
docker-compose up -d

# Backend runs separately on host machine
./mvnw spring-boot:run
```

## 📊 Monitoring & Logging

### Application Logs
- Structured JSON logging
- Security event logging
- Performance metrics
- Error tracking

### Health Checks
- `/actuator/health` - Application health
- `/actuator/metrics` - Application metrics
- Database connectivity checks
- Redis connectivity checks

## 🔄 Development Workflow

### ✅ What's Complete
- **Full MFA System**: WebAuthn, Email OTP, SMS OTP
- **Security Features**: Rate limiting, audit logging, JWT authentication
- **Frontend & Backend**: Complete full-stack application
- **Testing Suite**: Unit, integration, and API tests
- **Documentation**: Comprehensive guides and API docs
- **Local Development Setup**: Docker Compose for infrastructure (PostgreSQL & Redis)
- **Deployment Ready**: Configured for Render (backend), Vercel (frontend), and Neon.tech (database)

### 🛠️ Manual Development Flow

#### Complete Local Development Setup

**Terminal 1 - Infrastructure:**
```bash
# Start PostgreSQL and Redis
docker-compose up -d postgres redis

# Monitor logs
docker-compose logs -f
```

**Terminal 2 - Backend:**
```bash
# Start Spring Boot with dev profile
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=dev"

# Or with hot reload (if using IDE with Spring Boot DevTools)
# Changes will auto-reload
```

**Terminal 3 - Frontend:**
```bash
cd frontend
python3 server.py 3000
```

#### Development Tips

1. **Hot Reload**: Spring Boot DevTools is included - changes auto-reload
2. **Database Changes**: With `ddl-auto=create` in dev profile, schema updates automatically
3. **Logs**: Check application logs for debugging
4. **Database Access**: Connect to PostgreSQL at `localhost:5432` with credentials from `.env`
5. **Redis Access**: Connect to Redis at `localhost:6379`

#### Common Development Tasks

**Reset Database:**
```bash
docker-compose down -v
docker-compose up -d postgres redis
# Database will be recreated on next backend start
```

**View Database:**
```bash
# Using psql
docker exec -it authx-postgres psql -U authx_user -d authx

# Or use a GUI tool like DBeaver, pgAdmin, etc.
# Connection: localhost:5432, user: authx_user, password: authx_pass, db: authx
```

**Clear Redis Cache:**
```bash
docker exec -it authx-redis redis-cli FLUSHALL
```

**Check Application Logs:**
```bash
# Backend logs appear in the terminal running Spring Boot
# Or check Docker logs
docker-compose logs postgres
docker-compose logs redis
```

### 📋 What's Next (Optional Enhancements)
- OAuth2 social login integration (Google, GitHub, Microsoft)
- Advanced monitoring and metrics dashboard
- Kubernetes deployment manifests
- Mobile app authentication flows

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

For support and questions:
- Create an issue in the repository
- Check the [Wiki](wiki) for detailed documentation
- Review the [API Documentation](http://localhost:8080/swagger-ui.html)

---

**AuthX** - Secure, Scalable, Production-Ready MFA System