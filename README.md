# AuthX - Multi-Factor Authentication System

A production-grade Multi-Factor Authentication system built with Spring Boot, featuring WebAuthn (FIDO2) biometric authentication, OTP fallback, and comprehensive security features.

## 🚀 Features

- **Multi-Factor Authentication**: WebAuthn (FIDO2) + OTP fallback
- **JWT-based Authentication**: Stateless with refresh token support
- **Role-Based Access Control (RBAC)**: Flexible permission system
- **Security Hardening**: Rate limiting, account lockout, audit logging
- **Redis Integration**: Session management and OTP storage
- **PostgreSQL**: Primary data persistence
- **Cloud-Ready**: Environment-based configuration for AWS deployment

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
- **Docker** (for PostgreSQL and Redis)
- **Java 17+** (for Spring Boot backend)
- **Python 3** (optional, for frontend server)

### 1. Start Infrastructure
```bash
# Start database and cache
docker-compose up -d postgres redis
```

### 2. Start Backend
```bash
# Compile and run Spring Boot application
./mvnw clean compile
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=dev"
```

### 3. Start Frontend (Optional)
```bash
# In another terminal
cd frontend
python3 server.py 3000
```

### Access Your Application
- **🌐 Web Interface**: http://localhost:3000
- **📚 API Documentation**: http://localhost:8080/swagger-ui.html
- **❤️ Health Check**: http://localhost:8080/api/v1/actuator/health

📖 **Detailed Setup Guide**: See [MANUAL_SETUP.md](MANUAL_SETUP.md)

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

### API Testing
```bash
# Unit tests
./mvnw test

# Integration tests
./mvnw test -Dtest=**/*IntegrationTest

# Manual API test
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","password":"Test123!"}'
```

### Frontend Testing
```bash
# Start frontend server
cd frontend && python3 server.py 3000

# Open: http://localhost:3000
# Test registration, login, MFA features
```

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

### AWS Elastic Beanstalk

1. **Prepare application**:
```bash
./mvnw clean package
```

2. **Create Beanstalk application**:
- Upload `target/authx-mfa-system-1.0.0.jar`
- Configure environment variables
- Set up RDS (PostgreSQL) and ElastiCache (Redis)

3. **Environment Configuration**:
```bash
# Set in Beanstalk environment
SPRING_PROFILE=prod
DB_HOST=your-rds-endpoint
REDIS_HOST=your-elasticache-endpoint
JWT_SECRET=your-production-secret
```

### Docker Deployment

```bash
# Build and run
docker-compose up --build -d

# Scale application
docker-compose up --scale authx-app=3
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
- **Docker Setup**: Full containerization with docker-compose

### 🛠️ Manual Development Flow
```bash
# 1. Start infrastructure
docker-compose up -d postgres redis

# 2. Start backend
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=dev"

# 3. Start frontend (optional)
cd frontend && python3 server.py 3000

# 4. Make changes and restart as needed
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