# Vault Policy for NATS Client
path "secret/data/nats" {
  capabilities = ["read"]
}

path "secret/metadata/nats" {
  capabilities = ["read"]
}