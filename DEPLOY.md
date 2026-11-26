# Deploying to Google Kubernetes Engine (GKE)

This runs the app on Google Cloud for the final presentation: the Spring Boot app and MySQL 8 as
pods in a GKE Autopilot cluster, with a public IP. It is a **temporary** deployment: delete it
after the course (see [Tear it down](#tear-it-down)).

```
  Browser ──► Load balancer (public IP, port 80)
                 │
                 ▼
        ┌──── GKE Autopilot cluster, namespace "inventory" ─────┐
        │  inventory-app (Deployment, 1 pod)  ──►  mysql:3306     │
        │     Spring Boot, port 8081              (StatefulSet,   │
        │                                          5 GB disk)     │
        └─────────────────────────────────────────────────────────┘
  GitHub push to main ─► CI (tests) ─► Deploy workflow ─► new image in Artifact Registry ─► rollout
```

| File | What it is |
|---|---|
| `k8s/namespace.yaml` | The `inventory` namespace everything lives in |
| `k8s/mysql.yaml` | MySQL 8 StatefulSet with a persistent disk, plus its in-cluster Service |
| `k8s/app.yaml` | The app Deployment (health checks, resources, secrets) and the public LoadBalancer Service |
| `.github/workflows/deploy.yml` | Automatic deploy after CI passes on `main` |

**Cost:** the cluster's management fee is covered by GKE's free tier. You pay for the pods
(about 0.75 vCPU and 2 GB in total, on cheaper Spot capacity) and the load balancer: roughly
**$10-15 for two weeks**. Set the budget alert in step 1.

Run everything below in **Cloud Shell** (the `>_` button at the top of the Google Cloud console).
It already has `gcloud`, `kubectl`, `docker` and `git`.

---

## 1. Billing, credits and a budget alert

1. In the [Google Developer Program profile](https://developers.google.com/profile), activate the
   Google AI Pro benefits and apply the monthly Google Cloud credit to your billing account.
2. In the Google Cloud console, create a project (e.g. `inventory-cps714`) and link that billing account.
3. **Billing -> Budgets & alerts -> Create budget**: $10, alerts at 50%, 90% and 100%.

## 2. Settings for this session

```bash
export PROJECT_ID=inventory-cps714          # your project ID
export REGION=northamerica-northeast2       # Toronto
export CLUSTER=inventory-cluster
export IMAGE_REPO=$REGION-docker.pkg.dev/$PROJECT_ID/inventory

gcloud config set project $PROJECT_ID
gcloud services enable container.googleapis.com artifactregistry.googleapis.com \
    iamcredentials.googleapis.com sts.googleapis.com
```

If Cloud Shell restarts, run the `export` lines again.

## 3. Create the cluster and the image registry (about 10 minutes)

```bash
# Autopilot: Google manages the machines; we only describe the pods
gcloud container clusters create-auto $CLUSTER --region=$REGION

# Private Docker registry for the app's images
gcloud artifacts repositories create inventory --repository-format=docker --location=$REGION

# Point kubectl at the new cluster
gcloud container clusters get-credentials $CLUSTER --region=$REGION
```

## 4. Build and push the app image

```bash
git clone https://github.com/gabtlv/Small-Business-Inventory-Sales-System.git
cd Small-Business-Inventory-Sales-System

export IMAGE=$IMAGE_REPO/app:$(git rev-parse --short HEAD)
gcloud auth configure-docker $REGION-docker.pkg.dev --quiet
docker build -t $IMAGE .
docker push $IMAGE
```

## 5. Create the secrets (passwords are never committed to git)

Pick the Admin and Staff passwords you'll give the TA. The database passwords are random.

```bash
kubectl apply -f k8s/namespace.yaml

kubectl create secret generic inventory-secrets -n inventory \
  --from-literal=admin-password='CHOOSE-AN-ADMIN-PASSWORD' \
  --from-literal=staff-password='CHOOSE-A-STAFF-PASSWORD' \
  --from-literal=db-password="$(openssl rand -base64 18)" \
  --from-literal=db-root-password="$(openssl rand -base64 18)"
```

Forgot one? `kubectl get secret inventory-secrets -n inventory -o jsonpath='{.data.admin-password}' | base64 -d`

The Admin/Staff passwords are only used when the accounts are first created. To change them
later, delete the MySQL data (step "Start over" below) or change them in the database.

## 6. Deploy

```bash
# Check the files against the real cluster first (nothing is created)
kubectl apply --dry-run=server -f k8s/mysql.yaml
sed "s|IMAGE_PLACEHOLDER|$IMAGE|" k8s/app.yaml | kubectl apply --dry-run=server -f -

# Deploy for real
kubectl apply -f k8s/mysql.yaml
sed "s|IMAGE_PLACEHOLDER|$IMAGE|" k8s/app.yaml | kubectl apply -f -

# Wait for it to start (the first time takes a few minutes: new Spot nodes + Spring Boot startup)
kubectl rollout status statefulset/mysql -n inventory --timeout=10m
kubectl rollout status deployment/inventory-app -n inventory --timeout=10m

# The public address (EXTERNAL-IP; may show <pending> for a minute)
kubectl get service inventory-app -n inventory
```

Open `http://EXTERNAL-IP` and log in with the passwords from step 5.

### Useful commands

| Command | What it does |
|---|---|
| `kubectl get pods -n inventory` | See the pods and whether they're running |
| `kubectl logs deployment/inventory-app -n inventory` | The app's logs (add `-f` to follow) |
| `kubectl describe pod POD_NAME -n inventory` | Why a pod isn't starting (see Events at the bottom) |
| `kubectl rollout restart deployment/inventory-app -n inventory` | Restart the app |
| `kubectl rollout undo deployment/inventory-app -n inventory` | Go back to the previous version |

### Save money between demos

Stop the pods (data on the MySQL disk is kept):

```bash
kubectl scale deployment/inventory-app -n inventory --replicas=0
kubectl scale statefulset/mysql -n inventory --replicas=0
```

Start again: the same two commands with `--replicas=1` (MySQL first).

### Start over with an empty database

```bash
kubectl delete statefulset mysql -n inventory
kubectl delete pvc data-mysql-0 -n inventory
kubectl apply -f k8s/mysql.yaml
kubectl rollout restart deployment/inventory-app -n inventory
```

---

## 7. Automatic deploys from GitHub (optional)

After this, every push to `main` that passes CI is deployed automatically
(`.github/workflows/deploy.yml`). GitHub signs in with **Workload Identity Federation**: no key
file or password is stored in GitHub.

```bash
export PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')
export REPO=gabtlv/Small-Business-Inventory-Sales-System

# A service account that is allowed to push images and deploy to GKE, nothing else
gcloud iam service-accounts create github-deployer --display-name="GitHub Actions deployer"
export SA=github-deployer@$PROJECT_ID.iam.gserviceaccount.com
gcloud artifacts repositories add-iam-policy-binding inventory --location=$REGION \
  --member="serviceAccount:$SA" --role="roles/artifactregistry.writer"
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA" --role="roles/container.developer"

# Trust GitHub's tokens, but only for this repository
gcloud iam workload-identity-pools create github --location=global --display-name="GitHub Actions"
gcloud iam workload-identity-pools providers create-oidc inventory-repo \
  --location=global --workload-identity-pool=github \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository == '$REPO'"
gcloud iam service-accounts add-iam-policy-binding $SA \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$REPO"

# Values for GitHub
echo "WIF_PROVIDER=projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/inventory-repo"
echo "WIF_SERVICE_ACCOUNT=$SA"
```

Then in GitHub: **Settings -> Secrets and variables -> Actions -> Variables tab -> New repository
variable**, and add:

| Variable | Value |
|---|---|
| `GCP_PROJECT_ID` | your project ID |
| `GCP_REGION` | `northamerica-northeast2` |
| `GKE_CLUSTER` | `inventory-cluster` |
| `WIF_PROVIDER` | printed above |
| `WIF_SERVICE_ACCOUNT` | printed above |

Test it: **Actions -> Deploy -> Run workflow**. The run's summary shows the address.

---

## Tear it down

After the course is marked, delete everything so nothing keeps costing credits:

```bash
kubectl delete namespace inventory                       # app, MySQL, disk, load balancer
gcloud container clusters delete $CLUSTER --region=$REGION
gcloud artifacts repositories delete inventory --location=$REGION
gcloud iam workload-identity-pools delete github --location=global   # if you did step 7
gcloud iam service-accounts delete github-deployer@$PROJECT_ID.iam.gserviceaccount.com
```

Then delete the `GKE_CLUSTER` repository variable in GitHub, which switches the Deploy
workflow off. Finally, check **Billing -> Reports** a day later to confirm costs stopped.
