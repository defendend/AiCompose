#!/bin/bash

# Server Setup Script for AiCompose Backend
# Run this script on your Yandex Cloud VPS as root or with sudo

set -e

echo "🚀 Setting up AiCompose Backend on Yandex Cloud VPS"

# Update system
echo "📦 Updating system packages..."
apt-get update
apt-get upgrade -y

# Install Java 21
echo "☕ Installing Java 21..."
apt-get install -y openjdk-21-jdk

# Create application user
echo "👤 Creating application user..."
if ! id -u aicompose > /dev/null 2>&1; then
    useradd -r -s /bin/false aicompose
fi

# Create application directory
echo "📁 Creating application directory..."
mkdir -p /opt/aicompose
chown aicompose:aicompose /opt/aicompose

# Create environment file
echo "🔐 Creating environment file..."
cat > /opt/aicompose/.env <<EOF
DEEPSEEK_API_KEY=your_deepseek_api_key_here
EOF

chmod 600 /opt/aicompose/.env
chown aicompose:aicompose /opt/aicompose/.env

echo "⚠️  IMPORTANT: Edit /opt/aicompose/.env and add your DeepSeek API key!"

# Create systemd service
echo "⚙️  Creating systemd service..."
cat > /etc/systemd/system/aicompose.service <<'EOF'
[Unit]
Description=AiCompose Backend
After=network.target

[Service]
Type=simple
User=aicompose
Group=aicompose
WorkingDirectory=/opt/aicompose
EnvironmentFile=/opt/aicompose/.env
ExecStart=/usr/bin/java -jar /opt/aicompose/aicompose-backend.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=aicompose

# Security hardening
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/aicompose

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd
systemctl daemon-reload
systemctl enable aicompose

echo "✅ Systemd service created"

# Configure sudo permissions for deployment
echo "🔑 Configuring sudo permissions..."
cat > /etc/sudoers.d/aicompose-deploy <<EOF
defendend ALL=(ALL) NOPASSWD: /bin/mkdir, /bin/cp, /bin/chown, /bin/chmod, /bin/systemctl restart aicompose, /bin/systemctl start aicompose, /bin/systemctl status aicompose, /bin/systemctl is-active aicompose
EOF

chmod 440 /etc/sudoers.d/aicompose-deploy

echo "✅ Sudo permissions configured"

# Install nginx if not installed
if ! command -v nginx &> /dev/null; then
    echo "🌐 Installing Nginx..."
    apt-get install -y nginx
fi

# Configure Nginx
echo "🌐 Configuring Nginx..."
cat > /etc/nginx/sites-available/aicompose <<'NGINX_EOF'
server {
    listen 80;
    server_name _;

    # Backend API
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;

        # Timeouts for long-running requests
        proxy_read_timeout 120s;
        proxy_connect_timeout 120s;
        proxy_send_timeout 120s;
    }

    # Health check
    location /health {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
    }

    # Logs
    access_log /var/log/nginx/aicompose.access.log;
    error_log /var/log/nginx/aicompose.error.log;
}
NGINX_EOF

# Enable site
ln -sf /etc/nginx/sites-available/aicompose /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default

# Test and reload Nginx
nginx -t && systemctl reload nginx
echo "✅ Nginx configuration updated"

# Open firewall ports
echo "🔥 Configuring firewall..."
if command -v ufw &> /dev/null; then
    ufw allow 22/tcp
    ufw allow 80/tcp
    ufw allow 443/tcp
    ufw --force enable
fi

echo ""
echo "========================================="
echo "✅ Server setup complete!"
echo "========================================="
echo ""
echo "Next steps:"
echo "1. Edit DeepSeek API key in /opt/aicompose/.env"
echo "   sudo nano /opt/aicompose/.env"
echo ""
echo "2. Deploy your JAR (will be done automatically via GitHub Actions)"
echo ""
echo "3. Start the service manually (for testing):"
echo "   sudo systemctl start aicompose"
echo ""
echo "4. Check status:"
echo "   sudo systemctl status aicompose"
echo "   sudo journalctl -u aicompose -f"
echo ""
echo "Backend will be available at: http://89.169.190.22/api/"
echo "========================================="
