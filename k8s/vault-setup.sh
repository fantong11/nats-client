#!/bin/bash

# Vault setup script for NATS Client

# 1. Enable Kubernetes auth method
vault auth enable kubernetes

# 2. Configure Kubernetes auth
vault write auth/kubernetes/config \
    token_reviewer_jwt="$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
    kubernetes_host="https://$KUBERNETES_PORT_443_TCP_ADDR:443" \
    kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt

# 3. Create policy
vault policy write nats-client - <<EOF
path "secret/data/nats" {
  capabilities = ["read"]
}

path "secret/metadata/nats" {
  capabilities = ["read"]
}
EOF

# 4. Create role
vault write auth/kubernetes/role/nats-client \
    bound_service_account_names=nats-client \
    bound_service_account_namespaces=default \
    policies=nats-client \
    ttl=24h

# 5. Store NATS secrets in Vault
vault kv put secret/nats \
    url="nats://nats-server:4222" \
    username="nats_user" \
    password="nats_password"

echo "Vault setup completed for NATS Client"