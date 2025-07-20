Decoupling your build and deployment workflows is a strong best practice, aligning perfectly with "Build once, deploy many." GitHub Actions provides the features needed to achieve this using **reusable workflows**.

Here's how we'll implement this:

1. **GCP Setup (Pre-requisites for GitHub Actions):**

   - **Enable APIs:** Ensure `Cloud Run API`, `Artifact Registry API`, and `IAM Service Account Credentials API`, `IAM API`, `Workload Identity Federation API` are enabled in your GCP project.

   - **Create a GitHub Actions Service Account:** This service account will be used by your GitHub Actions workflows to interact with GCP.

     - Name: `github-actions-sa` (or similar)
     - Grant the following IAM roles:
       - `Artifact Registry Writer`: Allows pushing and pulling images from your `docker` Artifact Registry.
       - `Cloud Run Developer`: Allows deploying new revisions to Cloud Run.
       - `Service Account User`: Allows the GitHub Actions service account to act as the Cloud Run runtime service account (if you choose to specify one for your Cloud Run service, otherwise Cloud Run uses its default compute service account).

   - **Set up Workload Identity Federation:** This is the most secure way for GitHub Actions to authenticate with GCP, avoiding long-lived service account keys in GitHub secrets.

     - Create an Identity Pool: `gcloud iam workload-identity-pools create github-pool --location=global --display-name="GitHub Actions Pool"`

     - Create a Provider in the Pool for GitHub:

       Bash

       ```
       gcloud iam workload-identity-pools providers create-oidc github-provider \
         --location=global \
         --workload-identity-pool=github-pool \
         --display-name="GitHub Actions Provider" \
         --attribute-mapping="google.subject=assertion.sub" \
         --issuer-uri="https://token.actions.githubusercontent.com"
       ```

     - Grant the GitHub Actions Service Account permission to impersonate via the pool:

       Bash

       ```
       gcloud iam service-accounts add-iam-policy-binding github-actions-sa@sdcote.iam.gserviceaccount.com \
         --role="roles/iam.workloadIdentityUser" \
         --member="principalSet://iam.googleapis.com/projects/YOUR_GCP_PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/subject/repo:YOUR_GITHUB_ORG/YOUR_REPO_NAME:ref:refs/heads/main"
       ```

       **Replace `YOUR_GCP_PROJECT_NUMBER` (your project's numerical ID), `YOUR_GITHUB_ORG` (your GitHub username/org), and `YOUR_REPO_NAME` (your repo name).** You might want to broaden the `subject` to `repo:YOUR_GITHUB_ORG/YOUR_REPO_NAME:*` if you want all branches to trigger the build, or `repo:YOUR_GITHUB_ORG/YOUR_REPO_NAME` for any branch. For now, `main` is good.

2. GitHub Repository Secrets (Optional but Recommended):

   While Workload Identity Federation reduces the need for many secrets, you might still want to define:

   - `GCP_PROJECT_ID`: Your GCP project ID (`sdcote`).
   - `GCP_WORKLOAD_IDENTITY_PROVIDER`: The full resource name of your Workload Identity Provider (e.g., `projects/YOUR_GCP_PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/providers/github-provider`).
   - `GCP_SERVICE_ACCOUNT_EMAIL`: The email of your GitHub Actions service account (`github-actions-sa@sdcote.iam.gserviceaccount.com`).
   - `GCP_ARTIFACT_REGISTRY_LOCATION`: `us-east5`.
   - `GCP_CLOUD_RUN_REGION`: `us-central1` (or your preferred region for Cloud Run).
   - `CLOUD_RUN_SERVICE_NAME`: e.g., `my-webservices`.



### `pom.xml` (Current state is good)



Your `pom.xml` should already have the `jib-maven-plugin` configured to push to the correct regional registry:

XML

```
            <plugin>
                <groupId>com.google.cloud.tools</groupId>
                <artifactId>jib-maven-plugin</artifactId>
                <version>3.4.1</version>
                <configuration>
                    <from>
                        <image>openjdk:21-jdk-slim</image>
                    </from>
                    <to>
                        <image>us-east5-docker.pkg.dev/sdcote/docker/${project.artifactId}:${project.version}</image>
                    </to>
                    <tags>
                        <tag>latest</tag>
                    </tags>
                </configuration>
                <executions>
                    <execution>
                        <id>build-and-push-image</id>
                        <phase>deploy</phase>
                        <goals>
                            <goal>build</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-deploy-plugin</artifactId>
                <configuration>
                    <skip>true</skip>
                </configuration>
            </plugin>
```

And your `artifactId` is `webservices`, so the final image name will be `us-east5-docker.pkg.dev/sdcote/docker/webservices:0.0.1` (or whatever the version is).

------



### GitHub Actions Workflows



Create two workflow files in your `.github/workflows/` directory:

1. `.github/workflows/build.yml` (The "Build" workflow)
2. `.github/workflows/deploy.yml` (The "Deploy" reusable workflow)

------



### 1. `build.yml` (Build and Push, then Trigger Deploy)



This workflow will be triggered on a push to `main`, build your Spring Boot application with Jib, push the image to Artifact Registry, and then automatically call the `deploy.yml` workflow, passing the version.

YAML

```
# .github/workflows/build.yml
name: Build and Publish WebServices

on:
  push:
    branches:
      - main
  workflow_dispatch: # Allows manual triggering of this workflow

jobs:
  build_and_publish:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write # Required for Google Workload Identity Federation

    outputs:
      # Output the version so it can be passed to the deploy workflow
      built_version: ${{ steps.get-version.outputs.VERSION }}

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'

      - name: Authenticate to Google Cloud
        id: auth
        uses: google-github-actions/auth@v2
        with:
          project_id: ${{ secrets.GCP_PROJECT_ID }}
          workload_identity_provider: ${{ secrets.GCP_WORKLOAD_IDENTITY_PROVIDER }}
          service_account: ${{ secrets.GCP_SERVICE_ACCOUNT_EMAIL }}

      - name: Get Maven project version
        id: get-version
        run: |
          VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
          echo "VERSION=$VERSION" >> $GITHUB_OUTPUT
        working-directory: ${{ github.workspace }} # Ensure maven is run from the project root

      - name: Build and push Docker image with Jib
        # The 'deploy' phase will trigger Jib to build and push
        run: mvn clean install deploy
        working-directory: ${{ github.workspace }}
        env:
          # Jib automatically uses Application Default Credentials from google-github-actions/auth
          # No need for -Dmaven.deploy.skip=true as it's in pom.xml
          # No need for GOOGLE_APPLICATION_CREDENTIALS environment variable here.
          SPRING_PROFILES_ACTIVE: "github-actions" # Example: activate a specific Spring profile for CI builds

      - name: Call Deploy Workflow (Continuous Deployment)
        uses: ./.github/workflows/deploy.yml # Path to your reusable deploy workflow
        with:
          version: ${{ steps.get-version.outputs.VERSION }} # Pass the just-built version
        secrets: inherit # Pass all secrets from this workflow to the called workflow
```

------



### 2. `deploy.yml` (Reusable Deploy Workflow)



This workflow can be called by `build.yml` or manually triggered with a specific version.

YAML

```
# .github/workflows/deploy.yml
name: Deploy WebServices to Cloud Run

on:
  workflow_dispatch:
    inputs:
      artifact:
        description: 'Name of the artifact to deploy'
        required: true
        type: string
        default: "webservices"
      version:
        description: 'Version to deploy'
        required: true
        type: string
        default: "0.0.1"
  workflow_call:
    inputs:
      artifact:
        description: 'Artifact to deploy'
        required: true
        type: string
      version:
        description: 'Version to deploy'
        required: true
        type: string

jobs:
  deploy_to_cloud_run:
    runs-on: ubuntu-latest
    permissions:
      id-token: write # Required for Google Workload Identity Federation
      contents: read # Needed for actions/checkout if you include it, or other actions.

    steps:
      - name: Authenticate to Google Cloud
        id: auth
        uses: google-github-actions/auth@v2
        with:
          project_id: ${{ secrets.GCP_PROJECT_ID }}
          workload_identity_provider: ${{ secrets.GCP_WORKLOAD_IDENTITY_PROVIDER }}
          service_account: ${{ secrets.GCP_SERVICE_ACCOUNT_EMAIL }}

      - name: Deploy to Cloud Run
        uses: google-github-actions/deploy-cloudrun@v2
        with:
          service: ${{ secrets.CLOUD_RUN_SERVICE_NAME }} # e.g., 'my-webservices'
          # Use the input version, checking if it came from workflow_dispatch or workflow_call
          image: ${{ secrets.GCP_ARTIFACT_REGISTRY_LOCATION }}-docker.pkg.dev/${{ secrets.GCP_PROJECT_ID }}/docker/webservices:${{ github.event.inputs.version || github.event.workflow_call.inputs.version }}
          region: ${{ secrets.GCP_CLOUD_RUN_REGION }} # e.g., 'us-central1'
          allow_unauthenticated: true # Consider removing/securing this for production
          # Optional: Specify a service account for the Cloud Run instance itself
          # service_account_email: your-cloud-run-runtime-sa@sdcote.iam.gserviceaccount.com
          # env_vars: |
          #   SPRING_PROFILES_ACTIVE=prod
```



### Key Concepts Explained:



- **`workflow_dispatch`:** This trigger allows you to manually run a workflow from the GitHub Actions UI. It's crucial for the "Deploy many" part, letting you select any version to deploy. The `inputs` section defines the parameters you can pass (here, `version`).
- **`workflow_call`:** This trigger makes a workflow reusable and callable by other workflows. This is how `build.yml` can invoke `deploy.yml`. The `inputs` section defines what the calling workflow must pass.
- **`permissions: id-token: write`:** This permission is essential for Workload Identity Federation, allowing the workflow to request an OIDC token from GitHub.
- **`google-github-actions/auth@v2`:** This action uses the OIDC token to authenticate with GCP via Workload Identity Federation, setting up Application Default Credentials (ADC) for subsequent `gcloud` and Jib commands.
- **`mvn help:evaluate -Dexpression=project.version -q -DforceStdout`:** This Maven command is a robust way to extract the `project.version` from your `pom.xml` without building the entire project.
- **`outputs` in `build.yml`:** The `build_and_publish` job uses `outputs` to make the `built_version` available to other jobs or to workflows that call it.
- **`uses: ./.github/workflows/deploy.yml`:** This syntax is how you call a reusable workflow within the same repository.
- **`secrets: inherit`:** When calling a reusable workflow, this explicitly tells the called workflow to use the same secrets available to the calling workflow. This is very convenient for authentication.
- **Image URL Construction:** The `deploy.yml` constructs the full image path using the input `version` and the `artifactId` (`webservices`) which is assumed to be constant for your project. The `||` operator handles whether the version comes from a manual `workflow_dispatch` trigger (`github.event.inputs.version`) or an automatic `workflow_call` (`github.event.workflow_call.inputs.version`).
- **`google-github-actions/deploy-cloudrun@v2`:** This is a convenient action specifically for deploying to Cloud Run.

This setup provides a robust and secure way to build your Spring Boot services, push them to Artifact Registry, and then deploy them to Cloud Run, all while maintaining the "Build one; Deploy many" principle.


# Web Services Service Account!


Now that the core functionality is working, you can consider refining your setup further:

1.  **Refine Cloud Run Service Account:**

      * Currently, your Cloud Run service is likely running with the project's default Compute Engine service account (`PROJECT_NUMBER-compute@developer.gserviceaccount.com`).
      * **Best Practice:** Create a dedicated, least-privilege service account specifically for your Cloud Run service to run as. This improves security by isolating permissions. For example, if your `core-api` needs to access a Cloud SQL database, you'd grant *only* that new service account the `Cloud SQL Client` role, not the broad `Compute Engine default service account` or your `builder` account.
      * **How to do it:**
        1.  Create a new service account (e.g., `core-api-runner@sdcote.iam.gserviceaccount.com`).
        2.  Grant it *only* the specific IAM roles it needs to function (e.g., `roles/artifactregistry.reader` if it needs to pull images at runtime, `roles/cloudsql.client` if connecting to Cloud SQL, etc.).
        3.  Update your `deploy.yml` to specify this new service account for the Cloud Run service:
            ```yaml
            # ...
                - name: Deploy to Cloud Run
                  uses: google-github-actions/deploy-cloudrun@v2
                  with:
                    service: ${{ github.event.inputs.service_name || github.event.workflow_call.inputs.service_name }}
                    image: ${{ secrets.GCP_ARTIFACT_REGISTRY_LOCATION }}-docker.pkg.dev/${{ secrets.GCP_PROJECT_ID }}/docker/${{ github.event.inputs.artifact || github.event.workflow_call.inputs.artifact }}:${{ github.event.inputs.version || github.event.workflow_call.inputs.version }}
                    region: us-east5
                    service_account_email: core-api-runner@sdcote.iam.gserviceaccount.com # <--- ADD THIS LINE
                    # ... other optional parameters
            ```
            (Remember to grant the `builder` service account `iam.serviceaccounts.actAs` permission on `core-api-runner@sdcote.iam.gserviceaccount.com` if you use a custom service account for the Cloud Run service, just like you did for the default compute service account.)

2.  **Configure Environment Variables and Secrets (if applicable):**

      * If your `core-api` needs environment variables (e.g., `SPRING_PROFILES_ACTIVE=prod`) or access to secrets (e.g., API keys, database credentials), you can pass them via `env_vars` or `secret_env_vars` and `secret_volume_mounts` in the `deploy-cloudrun` action.
      * **Best Practice for Secrets:** Use Google Secret Manager to store secrets and grant your Cloud Run service account permission to access them. Do **not** put sensitive information directly into your GitHub repository or workflow YAML.

3.  **Traffic Management (Blue/Green, Canary Deployments):**

      * For production deployments, consider using Cloud Run's traffic splitting features (e.g., `to-revisions` or `no-traffic` then `to-revisions` in a separate step) for blue/green or canary deployments to minimize risk.

4.  **Automatic Triggering:**

      * You can now integrate `workflow_call` into a CI workflow that automatically builds and pushes your Docker image to Artifact Registry, then calls `deploy.yml`. This creates a fully automated CI/CD pipeline.
      * Example: A `push` to `main` branch triggers a build, which then calls `deploy.yml`.

5.  **Health Checks and Readiness Probes:**

      * Ensure your application has robust health checks configured so Cloud Run can correctly determine when a new revision is ready to receive traffic.

You've built a strong foundation. Continue iterating and adding features as your application matures\! Well done\!