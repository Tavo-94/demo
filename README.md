# Spring Boot 4 GitOps Demo Stack

This repository contains an enterprise-grade Spring Boot 4.1.1 application demonstrating a full observability pipeline (OpenTelemetry, Grafana Tempo, Splunk) running on a local lightweight Kubernetes cluster (K3s) orchestrated by Argo CD via a GitOps model.

## 🏗️ Architecture Overview

- **Backend:** Java 21, Spring Boot 4.1.1, Spring Data JPA
- **Database:** PostgreSQL 15
- **Observability:** Grafana (Dashboards), Grafana Tempo (W3C Distributed Tracing via OTLP), Splunk (Centralized Logging via HEC)
- **Infrastructure:** Native K3s running inside Ubuntu WSL2
- **Deployment:** Argo CD utilizing GitOps Sync Waves

---

## 🛠️ Prerequisites

Before you begin, ensure your local development environment has the following:
1. **Windows Host:** Java 21 installed.
2. **WSL2 (Ubuntu):** A running instance of Ubuntu via WSL2.
3. **K3s:** Installed natively inside your WSL2 Ubuntu environment.
4. **Argo CD:** Installed within your K3s cluster.
5. **Splunk:** A local instance of Splunk Enterprise running on Windows to receive logs. You can spin this up via Docker Desktop on Windows using:
   ```powershell
   # Executed in: Windows PowerShell
   docker run -d -p 8000:8000 -p 8088:8088 -e SPLUNK_START_ARGS="--accept-license" -e SPLUNK_PASSWORD="admin" -e SPLUNK_HEC_TOKEN="6c2439e8-874f-474c-8397-f924548296b2" --name splunk splunk/splunk:latest
   ```

---

## 🚀 Local Deployment Guide

Because this architecture bridges Windows and a Linux virtual machine (WSL), it is **critical** to run the specific commands in their designated terminal environments.

### Step 1: Build & Package the Application
**Terminal:** Windows Git Bash or PowerShell
**Location:** Project Root

```bash
./mvnw clean package -DskipTests
```
*Why this environment?* Running Maven on the Windows side avoids severe filesystem locking and synchronization issues that occur when accessing Windows drives from within WSL. This compiles the Java code and generates the executable `.jar` in the `target/` directory.

### Step 2: Containerize & Load Image into K3s
**Terminal:** Ubuntu WSL Terminal
**Location:** Project Root (`/mnt/c/projects/...`)

```bash
docker build -t demo-service:v1 .
docker save demo-service:v1 > demo-service.tar
sudo k3s ctr images import demo-service.tar
```
*Why these commands?* This builds the Docker image and packages it into a tarball. Because we are running K3s locally, we bypass the need to push the image to a public registry (like Docker Hub) by directly importing the tarball into K3s's internal `containerd` engine.
*(Note: Ensure you do not commit the `demo-service.tar` file to Git!)*

### Step 3: Patch WSL DNS for Argo CD
**Terminal:** Ubuntu WSL Terminal

```bash
kubectl get configmap coredns -n kube-system -o yaml | sed 's/forward . \/etc\/resolv.conf/forward . 8.8.8.8/' | kubectl apply -f -
kubectl rollout restart deployment coredns -n kube-system
```
*Why this command?* WSL2's virtual switch drops UDP packets originating from internal pod networks. This prevents K3s CoreDNS from resolving external domains (like `github.com`), breaking Argo CD. This patch forces CoreDNS to bypass the WSL switch and use Google DNS directly.

### Step 4: Deploy via Argo CD
**Terminal:** Web Browser (Windows)

1. Open Argo CD at `https://localhost:8081` (assuming you port-forwarded it during installation).
2. Click **+ NEW APP**.
3. **General:** Name: `demo-stack`, Project: `default`, Sync Policy: `Automatic`.
4. **Source:** Enter the URL to this GitHub repository, Revision: `main`, Path: `k8s`. *(Pointing to `k8s` ensures Argo CD only applies the manifest files, not the source code).*
5. **Destination:** Cluster: `https://kubernetes.default.svc`, Namespace: `default`.
6. Click **CREATE**.

*What happens next?* Argo CD will read the manifests and utilize **Sync Waves**. It will deploy Postgres, Tempo, and Grafana first (Wave 1). It will pause until Postgres is completely healthy, and only then deploy the Spring Boot API (Wave 2). This eliminates `Connection refused` crash loops!

### Step 5: Port-Forwarding & Accessing Services
**Terminal:** Two separate Ubuntu WSL Terminals

To access the cluster from your Windows browser, tunnel the internal services:

**Terminal A:**
```bash
kubectl port-forward svc/demo-service 8080:8080
```
**Terminal B:**
```bash
kubectl port-forward svc/grafana 3000:3000
```

1. Trigger the API transaction: `http://localhost:8080/demo`
2. View the resulting OTLP Trace waterfall: Open Grafana at `http://localhost:3000`, navigate to **Explore**, select **Tempo**, and search for the `traceId` found in your Spring Boot logs.

---

## 🌅 Day-to-Day Workflow: Starting the App (After a Reboot)

When you turn your computer on and want to resume development, follow these exact steps to bring the entire pipeline back online.

### Step 1: Start Splunk (Windows)
Because Splunk runs outside the Kubernetes cluster on your Windows host, you must start its Docker container.
**Terminal:** Windows PowerShell
```powershell
docker start splunk
```
*(If Docker Desktop was just opened, wait a few seconds for the Docker Engine to initialize before running this).*

### Step 2: Boot Up K3s (WSL)
Simply opening an Ubuntu terminal automatically boots the WSL engine. Since K3s runs as a systemd service, it will automatically start itself, and Argo CD will instantly sync your cluster.
**Terminal:** Ubuntu WSL Terminal
```bash
# Verify all pods are running and healthy
kubectl get pods -A
```

### Step 3: Re-Establish Port-Forwards (WSL)
Kubernetes port-forwards are temporary tunnels. You must reopen them to access the API and Grafana from your Windows browser.
**Terminal:** Two separate Ubuntu WSL Terminals
```bash
# Terminal A
kubectl port-forward svc/demo-service 8080:8080

# Terminal B
kubectl port-forward svc/grafana 3000:3000
```
Your entire stack is now operational!

---

## 🐛 Troubleshooting & Debugging

If a service isn't responding or a port-forward fails, use these standard Kubernetes debugging commands in your **WSL Terminal**.

### 1. "Connection Refused" during Port-Forwarding
If you run `kubectl port-forward` and receive a `failed to connect to localhost:xxxx inside namespace ... connection refused` error, **your cluster networking is fine.** This specifically means the application inside the pod has not started its web server yet, or it has silently crashed.
- **Grafana on WSL2 Quirk:** Grafana 11+ downloads bundled plugins on startup. Because WSL2 overlay filesystems have slow I/O, Grafana's internal SQLite database locks up (`SQLITE_BUSY`), severely delaying the web server boot. **Fix:** Simply wait 1-2 minutes for the plugins to finish installing.

### 2. Checking Pod Status
Check if a pod is crashing (`CrashLoopBackOff`) or healthy (`Running`):
```bash
kubectl get pods -A
```

### 3. Tailing Logs
To see exactly what a pod is doing (or why it hasn't bound to its port yet), follow its logs in real-time:
```bash
# Example: Follow Grafana's logs
kubectl logs -l app=grafana -f
```
*(Press `Ctrl + C` to stop watching).*

### 4. Debugging Argo CD State
If you need to see why an Argo CD sync is stuck without using the web UI, or if you just want to access the UI again, forward the Argo CD server:
```bash
kubectl port-forward svc/argocd-server -n argocd 8081:443
```
Then visit `https://localhost:8081`.

---

### Safe Shutdown
**Terminal:** Windows PowerShell

```powershell
wsl.exe --shutdown
```
*Why this command?* Instead of gracefully tearing down Kubernetes resources one by one, shutting down the WSL engine instantly suspends the lightweight Linux VM. This immediately frees all CPU and RAM on Windows. Upon your next login, simply open WSL and your entire Kubernetes stack will resume exactly as you left it.
