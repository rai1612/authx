# AuthX Frontend

A complete, modern web frontend for the AuthX Multi-Factor Authentication system.

## 🌟 Features

### ✅ **Complete Authentication Flow**
- User registration and login
- Multi-factor authentication with WebAuthn and OTP
- JWT token management with automatic refresh
- Secure logout

### ✅ **WebAuthn Integration**
- Biometric authentication (Touch ID, Face ID, Windows Hello)
- Hardware security key support (YubiKey, etc.)
- Credential management and registration
- Cross-platform compatibility

### ✅ **MFA Management**
- Enable/disable MFA
- Multiple authentication methods (Email OTP, SMS, WebAuthn)
- Preferred method selection
- Test functionality

### ✅ **User Dashboard**
- Profile management
- MFA status overview
- WebAuthn credential management
- Audit log viewing
- Real-time activity tracking

### ✅ **Modern UI/UX**
- Responsive design (mobile-first)
- Dark/light mode support
- Accessibility features (WCAG 2.1)
- Toast notifications
- Loading states and error handling

## 🚀 Quick Start

### Prerequisites
- AuthX backend running on `http://localhost:8080`
- Modern web browser with JavaScript enabled
- For WebAuthn: HTTPS or localhost environment

### 1. Start the Frontend Server

```bash
# From the authx root directory
cd frontend
python3 server.py

# Or specify a custom port
python3 server.py 8000

# Or serve from a different directory
python3 server.py /path/to/frontend 3000
```

### 2. Open in Browser
Navigate to `http://localhost:3000` in your web browser.

### 3. Test the Application

1. **Register a new user**
   - Click "Create Account"
   - Fill in the registration form
   - Submit to create your account

2. **Login**
   - Use your email and password
   - Complete MFA if enabled

3. **Setup WebAuthn** (Recommended)
   - Go to Dashboard
   - Click "Setup WebAuthn"
   - Follow the browser prompts
   - Use Touch ID, Face ID, or security key

4. **Enable MFA**
   - In Dashboard, click "Toggle MFA"
   - Test the authentication flow

## 📁 Project Structure

```
frontend/
├── index.html          # Main HTML file
├── css/
│   └── style.css       # Complete styling with responsive design
├── js/
│   ├── config.js       # Configuration and feature detection
│   ├── utils.js        # Utilities and API client
│   ├── auth.js         # Authentication logic
│   ├── webauthn.js     # WebAuthn implementation
│   ├── dashboard.js    # Dashboard functionality
│   └── app.js          # Main application controller
├── server.py           # Development server
└── README.md           # This file
```

## 🔧 Configuration

Edit `js/config.js` to customize:

```javascript
const CONFIG = {
    // Backend API URL
    API_BASE_URL: 'http://localhost:8080/api/v1',
    
    // WebAuthn settings
    WEBAUTHN: {
        RP_ID: 'localhost',
        RP_NAME: 'AuthX MFA System',
        TIMEOUT: 300000
    },
    
    // UI settings
    UI: {
        TOAST_DURATION: 5000,
        AUTO_REFRESH_INTERVAL: 300000
    }
};
```

## 🌐 Browser Compatibility

### Supported Browsers
- ✅ Chrome 67+ (full WebAuthn support)
- ✅ Firefox 60+ (full WebAuthn support)
- ✅ Safari 14+ (full WebAuthn support)
- ✅ Edge 18+ (full WebAuthn support)

### WebAuthn Requirements
- **HTTPS**: Required for WebAuthn (except localhost)
- **Secure Context**: Must be served over HTTPS or localhost
- **User Gesture**: WebAuthn requires user interaction

### Feature Detection
The app automatically detects browser capabilities and shows appropriate UI:
- WebAuthn support detection
- Storage availability
- HTTPS requirement warnings

## 📱 Mobile Support

### iOS Safari
- ✅ Touch ID and Face ID support
- ✅ Responsive design
- ✅ PWA capabilities

### Android Chrome
- ✅ Fingerprint and biometric support
- ✅ Hardware security key support
- ✅ Full WebAuthn compatibility

## 🔒 Security Features

### Authentication Security
- JWT token storage in localStorage
- Automatic token refresh
- Secure logout with token cleanup
- Session timeout handling

### WebAuthn Security
- Cryptographic credential verification
- Anti-phishing protection
- Biometric authentication
- Hardware security key support

### UI Security
- XSS prevention with HTML escaping
- CSRF protection (handled by backend)
- Secure credential storage
- Input validation and sanitization

## 🎨 Customization

### Theming
The CSS uses CSS variables for easy theming:

```css
:root {
    --primary-color: #2563eb;
    --success-color: #10b981;
    --error-color: #ef4444;
    /* ... more variables */
}
```

### Dark Mode
Automatic dark mode detection with manual override support:
- Respects system preference
- Manual toggle capability
- Consistent across all components

## 🧪 Testing

### Manual Testing Checklist

#### Authentication Flow
- [ ] User registration
- [ ] User login (no MFA)
- [ ] User login (with MFA)
- [ ] Password validation
- [ ] Email validation
- [ ] Error handling

#### WebAuthn Flow
- [ ] WebAuthn credential registration
- [ ] WebAuthn authentication
- [ ] Credential management
- [ ] Error handling
- [ ] Browser compatibility

#### MFA Management
- [ ] Enable/disable MFA
- [ ] Method preference
- [ ] Email OTP testing
- [ ] WebAuthn testing

#### Dashboard
- [ ] Profile display
- [ ] MFA status
- [ ] Credential management
- [ ] Audit log viewing

### Automated Testing
For automated testing, consider:
- Playwright or Selenium for E2E tests
- Jest for unit testing
- WebAuthn testing with virtual authenticators

## 🚀 Production Deployment

### HTTPS Requirements
For production deployment with WebAuthn:

1. **SSL Certificate**: Required for WebAuthn
2. **HTTPS Redirect**: Ensure all traffic uses HTTPS
3. **Secure Headers**: Add security headers

### Static Hosting Options
- **Netlify**: Drag and drop deployment
- **Vercel**: Git-based deployment
- **AWS S3 + CloudFront**: Scalable hosting
- **nginx**: Self-hosted option

### Example nginx Configuration
```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    root /path/to/frontend;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    # Security headers
    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
    add_header X-XSS-Protection "1; mode=block";
}
```

## 🔍 Troubleshooting

### Common Issues

#### WebAuthn Not Working
- ✅ Check HTTPS requirement
- ✅ Verify browser support
- ✅ Ensure user gesture
- ✅ Check console for errors

#### CORS Errors
- ✅ Ensure backend CORS is configured
- ✅ Check API_BASE_URL in config
- ✅ Verify backend is running

#### Authentication Failures
- ✅ Check backend connectivity
- ✅ Verify API endpoints
- ✅ Check browser console
- ✅ Validate token storage

### Debug Mode
Set localStorage item for debug logging:
```javascript
localStorage.setItem('authx_debug', 'true');
```

## 📈 Performance

### Optimization Features
- Lazy loading of components
- Efficient DOM manipulation
- Minimal external dependencies
- Optimized CSS with variables
- Responsive images and layouts

### Bundle Size
- **HTML**: ~15KB
- **CSS**: ~25KB
- **JavaScript**: ~35KB
- **Total**: ~75KB (uncompressed)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License.

## 🆘 Support

- Check the [Backend Documentation](../README.md)
- Review the [API Documentation](http://localhost:8080/swagger-ui.html)
- Open an issue in the repository

---

**AuthX Frontend** - Complete, secure, and modern web interface for multi-factor authentication.