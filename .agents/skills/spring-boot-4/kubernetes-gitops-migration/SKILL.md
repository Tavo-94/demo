---
name: kubernetes-gitops-migration
description: >
  Best practices and critical insights for migrating Spring Boot applications from 
  Docker Compose to K3s and Argo CD using a GitOps model.
---

# Kubernetes GitOps Migration (K3s + Argo CD)

This skill covers the critical "gotchas" and architecture patterns for migrating a Spring Boot application to a lightweight Kubernetes distribution (K3s) using Argo CD.

## Critical Insights & Gotchas

### 1. The WSL2 CoreDNS Blackhole
When running K3s natively on WSL2 (Ubuntu), the `coredns` pod forwards external DNS queries to `/etc/resolv.conf`. In WSL2, this points to the Windows virtual switch, which silently drops UDP DNS packets from container networks (like `10.42.x.x`). 
**Symptoms:** Argo CD fails to sync with `server misbehaving`, or pods cannot resolve external URLs.
**The Fix:** Patch the `coredns` ConfigMap to forward directly to a public DNS provider (e.g., Google DNS).
```bash
kubectl get configmap coredns -n kube-system -o yaml | sed 's/forward . \/etc\/resolv.conf/forward . 8.8.8.8/' | kubectl apply -f -
kubectl rollout restart deployment coredns -n kube-system
```

### 2. Large File Pushes in GitOps (The .tar Trap)
When converting a local Docker workflow to K3s, it's common to export images (e.g., `docker save > app.tar`) and load them directly into K3s containerd.
**The Trap:** If these `.tar` files are accidentally staged in Git, `git push` to GitHub will hang or timeout ("Empty reply from server") due to file size limits.
**The Fix:**
- Add `*.tar` and `*.log` to `.gitignore` immediately.
- If accidentally committed, standard `git rm --cached` isn't enough if it's the root commit. You may need to forcefully clear the history (`git update-ref -d HEAD`) or run aggressive garbage collection:
  ```bash
  git reflog expire --expire=now --all
  git gc --prune=now --force
  ```

### 3. Argo CD Sync Waves for Startup Orchestration
Unlike Docker Compose where `depends_on` handles ordering, Kubernetes Deployments all start simultaneously. If the Spring Boot API starts before Postgres, it will crash (`Connection refused`), triggering backoff loops.
**The Fix:** Use Argo CD Sync Waves annotations.
- Annotate Postgres with `"1"`: `argocd.argoproj.io/sync-wave: "1"`
- Annotate Spring Boot with `"2"`: `argocd.argoproj.io/sync-wave: "2"`
Argo CD will wait until Wave 1 is completely healthy before deploying Wave 2.

### 4. Container Command vs Args
When translating `docker-compose.yml` to Kubernetes YAML, be careful with `command:`.
In Kubernetes, `command:` overrides the container's ENTRYPOINT. If the base image uses an ENTRYPOINT and you supply `command: ["-config.file=..."]` (like for Grafana Tempo), Kubernetes will try to execute the string `-config.file` as a binary and crash.
**The Fix:** Use `args: ["-config.file=..."]` instead to correctly append arguments to the existing ENTRYPOINT.

### 5. Accessing the Host Network from Pods
In Docker Compose, services can use `http://localhost` to reach the host. In Kubernetes, `localhost` points inside the pod.
**The Fix:** To access a service running on the host (like Splunk), use the K3s default bridge IP (usually `10.42.0.1` or `172.x.x.x` depending on the CNI) instead of `localhost`.
