#!/usr/bin/env python3
"""
Simple HTTP server for AuthX frontend development
Usage: python3 server.py [port]
Default port: 3000
"""

import http.server
import socketserver
import sys
import os
from pathlib import Path

# Default port
PORT = 3000

# Get port from command line if provided
if len(sys.argv) > 1:
    try:
        PORT = int(sys.argv[1])
    except ValueError:
        print(f"Invalid port number: {sys.argv[1]}")
        print(f"Using default port: {PORT}")

# Get directory from command line if provided
DIRECTORY = Path(__file__).parent
if len(sys.argv) > 2:
    DIRECTORY = Path(sys.argv[2])
    if not DIRECTORY.exists():
        print(f"Directory does not exist: {DIRECTORY}")
        sys.exit(1)

os.chdir(DIRECTORY)

class CustomHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    """Custom request handler with CORS and proper MIME types"""
    
    def do_OPTIONS(self):
        """Handle CORS preflight requests"""
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization')
        self.send_header('Access-Control-Max-Age', '3600')
        self.end_headers()
    
    def end_headers(self):
        # Add CORS headers
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization')
        # Add security headers
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('X-Frame-Options', 'DENY')
        self.send_header('X-XSS-Protection', '1; mode=block')
        super().end_headers()
    
    def guess_type(self, path):
        """Override to ensure correct MIME types"""
        # Override for specific file types first
        if path.endswith('.js'):
            return 'application/javascript', None
        elif path.endswith('.css'):
            return 'text/css', None
        elif path.endswith('.html') or path == '/' or path.endswith('/'):
            return 'text/html', None
        
        # For other files, use parent's guess_type
        result = super().guess_type(path)
        
        # Ensure we always return a tuple (mimetype, encoding)
        if isinstance(result, tuple):
            return result
        elif isinstance(result, str):
            return result, None
        else:
            return 'application/octet-stream', None
    
    def send_head(self):
        """Override to ensure proper Content-Type header formatting"""
        path = self.translate_path(self.path)
        f = None
        try:
            # Handle directory requests - serve index.html
            if os.path.isdir(path):
                if not self.path.endswith('/'):
                    # redirect browser - doing basically what apache does
                    self.send_response(301)
                    self.send_header("Location", self.path + "/")
                    self.end_headers()
                    return None
                for index in "index.html", "index.htm":
                    index = os.path.join(path, index)
                    if os.path.exists(index):
                        path = index
                        break
                else:
                    return self.list_directory(path)
            
            # Open the file
            f = open(path, 'rb')
        except OSError:
            self.send_error(404, "File not found")
            return None
        
        try:
            fs = os.fstat(f.fileno())
            # Get proper content type from the actual path
            ctype, encoding = self.guess_type(self.path)
            
            # Format Content-Type header properly (extract string from tuple if needed)
            if isinstance(ctype, tuple):
                ctype = ctype[0] if ctype else 'application/octet-stream'
            if isinstance(encoding, tuple):
                encoding = encoding[0] if encoding else None
            
            # Build content type string
            if encoding:
                content_type = f'{ctype}; charset={encoding}'
            else:
                content_type = str(ctype)
            
            self.send_response(200)
            self.send_header("Content-type", content_type)
            self.send_header("Content-Length", str(fs[6]))
            self.send_header("Last-Modified", self.date_time_string(fs.st_mtime))
            self.end_headers()
            return f
        except:
            f.close()
            raise
    
    def log_message(self, format, *args):
        """Override to customize log messages"""
        # Only log errors, suppress normal requests for cleaner output
        if args[1] != '200':
            super().log_message(format, *args)

def main():
    """Start the server"""
    try:
        with socketserver.TCPServer(("", PORT), CustomHTTPRequestHandler) as httpd:
            print(f"🚀 AuthX Frontend Server")
            print(f"📁 Serving directory: {DIRECTORY}")
            print(f"🌐 Server running at http://localhost:{PORT}/")
            print(f"📝 Press Ctrl+C to stop the server")
            try:
                httpd.serve_forever()
            except KeyboardInterrupt:
                print("\n\n👋 Server stopped")
    except OSError as e:
        if e.errno == 98:  # Address already in use
            print(f"❌ Error: Port {PORT} is already in use")
            print(f"💡 Try using a different port:")
            print(f"   python3 server.py 3001")
            print(f"   python3 server.py 8000")
            print(f"\nOr stop the process using port {PORT}:")
            print(f"   lsof -ti:{PORT} | xargs kill")
        else:
            print(f"❌ Error starting server: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()

