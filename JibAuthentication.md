# Jib Authentication with GCP

Jib needs to authenticate with GCP to upload the image to the Artifact Registry. While this can be performed in a number of ways, our goal is to use just one `pom.xml` file that will allow for both building on a local workstation and on GitHub Actions runners.

The current approach is to let Jib detect which approach to take based on the state of the environment. If there is already an authenticated session, Jib uses it. If there is no session, Jib will look in and environment variable for the location of a key file to authenticate at that time.

This approach allow one `pom.xml` file to authenticate differently. If it is run from GitHub actions, a session will exist. If no session exists, one will be created based on the location of a key file provided by an environment variable.

## Local Workstation

When running on a local workstation, it is easiest to download the JSON key file for the system account set up with permissions to publish to the repository and place it in a location on the file system outside of the project root. Usually somewhere in the developer's home directory where it can be protected from other users.

Create an environment variable that contains the location of the JSON key file. Jib relies on the "GOOGLE_APPLICATION_CREDENTIALS" environment variable to point to the location of a JSON key file on the file system. If this environment variable is found, it will load the authentication credentials from the file specified in that variable and authenticate with GCP.

The environment variable can be set at the operating system shell level, or as an injected environment variable in the Integrated Development Environment (IDE). Setting the variable at the OS level is often preferred as not to lock in developer-specific configurations in any shared project files.

## GitHub Actions

The most secure way for GitHub Actions to authenticate with GCP is Workload Identity Federation (WIF), avoiding long-lived service account keys in GitHub secrets. WIF involves configuring GCP to trust token exchanges with GitHub allowing GitHub to request temporary tokens from GCP to perform work as a member of a "workload identity pool" that is granted specific permissions.

There is a separate step in the GitHub Actions workflows that authenticates to Google Cloud before the Maven build, and subsequently Jib, is called. When Jib attempts to push image layers, it senses the prior authentication with GCP and uses the authenticated service account.

## Approach Summary

The core of our strategy, allowing Jib to detect authentication based on the environment's state, is the **Application Default Credentials (ADC)** mechanism. Jib, like other Google Cloud client libraries, prioritizes ADC.

Here's a breakdown of why our approach is was selected:

- **Single `pom.xml`:** By *removing* the explicit `<auth>` block from Jib's configuration in `pom.xml`, you tell Jib to rely on ADC. This is crucial for consistency.
- **Local Workstation (ADC via `GOOGLE_APPLICATION_CREDENTIALS` file path):**
  - `GOOGLE_APPLICATION_CREDENTIALS` environment variable pointing to the JSON key file is a standard and well-supported way to provide credentials for ADC on a local machine.[1]
  - It bypasses the Windows environment variable length limitations because you're passing a *path*, not the entire JSON content.
  - It keeps sensitive credentials off the file system outside of a protected location.
- **GitHub Actions (ADC via Workload Identity Federation):**
  - The `google-github-actions/auth` action performs the WIF handshake and sets up temporary ADC credentials for the GitHub Actions runner.[2]
  - When Jib runs, it finds and uses these temporary credentials.
  - This is highly secure as no long-lived keys are stored in GitHub secrets or committed to our repo.

Jib "senses" the prior authentication because the `google-github-actions/auth` action (or `gcloud auth application-default login` locally) sets up the environment in a way that ADC can automatically discover the credentials.

### Conclusion:

Our approach is a sound and recommended way to set up Jib authentication in a mixed development and CI/CD environment with Google Cloud. It's clean, secure, and adheres to the principles of a well-architected build and deploy pipeline.

Multiple sources (like [1], [2], [3]) reinforce that **Application Default Credentials (ADC)** is the standard and recommended way for Google Cloud client libraries (which Jib uses) to find credentials.

Key points that validate our strategy:

- **ADC Prioritization:** ADC explicitly checks for the `GOOGLE_APPLICATION_CREDENTIALS` environment variable first, then for credentials set by `gcloud auth application-default login`, and finally for attached service accounts (like on Cloud Run or Compute Engine). [3] This order means our local setup and GitHub Actions will naturally find the credentials they need.
- **`GOOGLE_APPLICATION_CREDENTIALS` (File Path on Windows):** The documentation clearly states that you can use the `GOOGLE_APPLICATION_CREDENTIALS` environment variable to provide the *location* of a credential JSON file [1], which directly addresses the Windows environment variable length limitation by not trying to store the entire JSON content in the variable itself.
- **Workload Identity Federation for GitHub Actions:** Research (especially [2], [3], [4]) highlight WIF as the preferred, keyless, and secure method for GitHub Actions to authenticate with GCP, leveraging short-lived tokens and OIDC. The `google-github-actions/auth` action handles this seamlessly, making those temporary credentials available via ADC.
- **Jib's Compatibility:** Jib specifically tries ADC last for Google Artifact Registry (`*-docker.pkg.dev`) and `gcr.io` [4], which means it integrates perfectly with this strategy.[4]

In summary, our approach is not only viable but is the idiomatic and most secure way to handle Jib authentication across both Windows workstations and GitHub Actions, using a single, consistent `pom.xml` file.

### Sources

1 - https://cloud.google.com/docs/authentication/application-default-credentials

2 - https://www.firefly.ai/academy/setting-up-workload-identity-federation-between-github-actions-and-google-cloud-platform

3 - https://cloud.google.com/docs/authentication/set-up-adc-local-dev-environment#:~:text=Note%3A%20When%20you%20set%20the,other%20locations%20only%20if%20necessary.

4 - https://github.com/GoogleContainerTools/jib/blob/master/jib-maven-plugin/CHANGELOG.md


# Jib has a Defect (#4401) that prevents ACD from being recognized.
The build and publish operations must be performed using local workstation using the JSON key of the service account until this defect is resolved.



# Authenticating in GitHub Actions Workflows

Prior to any interactions with GCP, the GitHub Actions workflow must authenticate with GCP. Adding the following step early in the workflow will authenticate and allow interactions with GCP:

```yaml
- name: Authenticate to Google Cloud
  id: auth
  uses: google-github-actions/auth@v2
  with:
    project_id: ${{ secrets.GCP_PROJECT_ID }}
    workload_identity_provider: projects/${{ secrets.GCP_PROJECT_NUMBER }}/locations/global/workloadIdentityPools/gha-pool-webservices/providers/github-sdcote-webservices
    service_account: ${{ secrets.GCP_SERVICE_ACCOUNT_EMAIL }}
```

Add these as repository secrets in our GitHub repository (**Settings > Secrets and variables > Actions**):

- `GCP_PROJECT_ID`: Value of `${GCP_PROJECT_ID}` (e.g., `sdcote`)
- `GCP_WORKLOAD_IDENTITY_PROVIDER`: Value of `${WIF_PROVIDER_NAME}` (e.g., `projects/1234567890/locations/global/workloadIdentityPools/gha-pool-webservices/providers/github-sdcote-webservices`)
- `GCP_SERVICE_ACCOUNT_EMAIL`: Value of `${GCP_SERVICE_ACCOUNT_EMAIL}` (e.g., `github-actions-builder-deployer@sdcote.iam.gserviceaccount.com`)

Adding the above step and adding the GitHub Secrets to our repository should allow our workflows to authenticate with GCP and allow any components that support Application Default Credentials to perform authenticated GCP operations.

# Setting up Workload Identify Federation in GCP

Let's detail the necessary steps to set up Workload Identity Federation (WIF) in GCP, which is the core of how our `Authenticate to Google Cloud` step in our build workflow will function.

**Assumptions:**

- We have a Google Cloud Project (e.g., `sdcote`).
- We have a GitHub repository (e.g., `YOUR_GITHUB_ORG/YOUR_REPO_NAME`).
- We have the `gcloud` CLI installed and configured locally to our GCP project.
- We're using the `main` branch for our primary workflow, but the setup can be adapted for other branches or tags.

These steps will create the necessary GCP resources and IAM policies to allow our GitHub Actions workflow to impersonate a GCP Service Account without storing long-lived keys.

## Step 1: Enable Necessary APIs

Ensure these APIs are enabled in our Google Cloud Project. We can enable them via the Cloud Console or `gcloud` CLI.

```bash
gcloud services enable \
    iam.googleapis.com \
    iamcredentials.googleapis.com \
    sts.googleapis.com \
    cloudresourcemanager.googleapis.com \
    artifactregistry.googleapis.com \
    run.googleapis.com
```

- `iam.googleapis.com`: Identity and Access Management API
- `iamcredentials.googleapis.com`: IAM Service Account Credentials API (for generating short-lived tokens)
- `sts.googleapis.com`: Security Token Service API (for token exchange in WIF)
- `cloudresourcemanager.googleapis.com`: Required for certain IAM operations.
- `artifactregistry.googleapis.com`: To interact with Artifact Registry.
- `run.googleapis.com`: To interact with Cloud Run.

## Step 2: Create a Dedicated GCP Service Account for GitHub Actions

This service account will be the identity that our GitHub Actions workflow *assumes*. It should have only the permissions necessary for its tasks (least privilege).

```bash
# Replace 'sdcote' with our actual GCP Project ID
export GCP_PROJECT_ID="sdcote"
export GITHUB_ACTIONS_SA_NAME="github-actions-builder-deployer" # A descriptive name

gcloud iam service-accounts create "${GITHUB_ACTIONS_SA_NAME}" \
    --display-name="Service Account for GitHub Actions Builds and Deployments" \
    --project="${GCP_PROJECT_ID}"

# Store the service account email for later use
export GCP_SERVICE_ACCOUNT_EMAIL="${GITHUB_ACTIONS_SA_NAME}@${GCP_PROJECT_ID}.iam.gserviceaccount.com"
echo "Created Service Account: ${GCP_SERVICE_ACCOUNT_EMAIL}"
```



## Step 3: Grant IAM Roles to the Service Account

Grant the `github-actions-builder-deployer` service account the necessary permissions.

```bash
# Grant Artifact Registry Writer for pushing/pulling images
gcloud projects add-iam-policy-binding "${GCP_PROJECT_ID}" \
    --member="serviceAccount:${GCP_SERVICE_ACCOUNT_EMAIL}" \
    --role="roles/artifactregistry.writer"

# Grant Cloud Run Developer for deploying to Cloud Run
gcloud projects add-iam-policy-binding "${GCP_PROJECT_ID}" \
    --member="serviceAccount:${GCP_SERVICE_ACCOUNT_EMAIL}" \
    --role="roles/run.developer"

# Grant Service Account User, needed if the Cloud Run service itself will run as a specific SA
# and the github-actions-builder-deployer SA needs to configure that
gcloud projects add-iam-policy-binding "${GCP_PROJECT_ID}" \
    --member="serviceAccount:${GCP_SERVICE_ACCOUNT_EMAIL}" \
    --role="roles/iam.serviceAccountUser"

# Optional: If you use the older gcr.io paths alongside pkg.dev, you might need Storage Object Creator
# gcloud projects add-iam-policy-binding "${GCP_PROJECT_ID}" \
#     --member="serviceAccount:${GCP_SERVICE_ACCOUNT_EMAIL}" \
#     --role="roles/storage.objectCreator"
```

Of course, we can use the portal to grant permissions via the IAM section.

#### Step 4: Create a Workload Identity Pool

A workload identity pool is a container for external identities that you want to federate with Google Cloud.

A pool will be dedicated to a repository so when we create pool IDs and Descriptions or Display Names, they should be traceable to the repository.

```bash
export WIF_POOL_ID="gha-pool-webservices" # A unique ID for the pool, be sure to trace it back to a repo
export WIF_POOL_DISPLAY_NAME="GHA Federation Pool for the sdcote/webservices repo"

gcloud iam workload-identity-pools create "${WIF_POOL_ID}" \
    --display-name="${WIF_POOL_DISPLAY_NAME}" \
    --location="global" \
    --project="${GCP_PROJECT_ID}"

# Get the full resource name of the pool (you'll need this for GitHub Secrets)
export WIF_POOL_NAME=$(gcloud iam workload-identity-pools describe "${WIF_POOL_ID}" \
    --location="global" \
    --project="${GCP_PROJECT_ID}" \
    --format="value(name)")
echo "Workload Identity Pool Name: ${WIF_POOL_NAME}"
```



#### Step 5: Create a Workload Identity Provider for GitHub

This provider configures the pool to trust OIDC tokens issued by GitHub. This will also be linked to an organization/repo on GitHub so be sure to name it accordingly to it is traceable.

```bash
export WIF_PROVIDER_ID="github-sdcote-webservices" # A unique ID for the provider make sure it's tracable to an org/repo
export WIF_PROVIDER_DISPLAY_NAME="GitHub Actions OIDC Provider"
export GITHUB_ORG="sdcote" # Replace with our GitHub Organization or Username
export GITHUB_REPO_NAME="webservices" # Replace with our GitHub Repository Name

gcloud iam workload-identity-pools providers create-oidc "${WIF_PROVIDER_ID}" \
    --workload-identity-pool="${WIF_POOL_ID}" \
    --display-name="${WIF_PROVIDER_DISPLAY_NAME}" \
    --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository,attribute.ref=assertion.ref,attribute.repository_owner=assertion.repository_owner" \
    --attribute-condition="attribute.repository_owner=='${GITHUB_ORG}' && attribute.repository=='${GITHUB_REPO_NAME}'" \
    --location="global" \
    --project="${GCP_PROJECT_ID}"

# Get the full resource name of the provider (you'll need this for GitHub Secrets)
export WIF_PROVIDER_NAME=$(gcloud iam workload-identity-pools providers describe "${WIF_PROVIDER_ID}" \
    --workload-identity-pool="${WIF_POOL_ID}" \
    --location="global" \
    --project="${GCP_PROJECT_ID}" \
    --format="value(name)")
echo "Workload Identity Provider Name: ${WIF_PROVIDER_NAME}"
```

**Explanation of `--attribute-mapping` and `--attribute-condition`:**

- `--attribute-mapping`: Tells GCP how to extract information (claims) from the GitHub OIDC token. `assertion.sub` (the subject, unique ID of the workflow run) is mapped to `google.subject`. Other `assertion` values (like actor, repository) are mapped to custom `attribute.` names.
- `--attribute-condition`: This is crucial for security. It acts as a filter, ensuring that only OIDC tokens from our *specific GitHub repository* are accepted by this provider. You can make this more granular (e.g., specific branch `attribute.ref == 'refs/heads/main'`) if needed. For now, `repository_owner` and `repository` are good.



## Step 6: Grant Workload Identity User Role to the Service Account

This is the final binding that allows our GitHub Actions workflow (identified by the OIDC token) to *impersonate* the `github-actions-builder-deployer` service account. This involves adding the "Workload Identity User" role to the service account and adding the service account to the workload identity pool

```bash
# Get our numerical project ID (used in the principalSet)
export GCP_PROJECT_NUMBER=$(gcloud projects describe "${GCP_PROJECT_ID}" --format="value(projectNumber)")
echo "GCP Project Number: ${GCP_PROJECT_NUMBER}"

gcloud iam service-accounts add-iam-policy-binding "${GCP_SERVICE_ACCOUNT_EMAIL}" \
    --role="roles/iam.workloadIdentityUser" \
    --member="principal://iam.googleapis.com/projects/${GCP_PROJECT_NUMBER}/locations/global/workloadIdentityPools/${WIF_POOL_ID}/subject/repo:${GITHUB_ORG}/${GITHUB_REPO_NAME}" \
    --project="${GCP_PROJECT_ID}"
```

**Explanation of `--member`:**

- `principalSet://...`: This specifies that the member is an external identity from our WIF pool.
- `projects/${GCP_PROJECT_NUMBER}/locations/global/workloadIdentityPools/${WIF_POOL_ID}`: Identifies our specific Workload Identity Pool.
- `subject/repo:${GITHUB_ORG}/${GITHUB_REPO_NAME}`: This is the critical part. It means "any subject (workflow run) originating from this specific GitHub repository." This maps directly to the `assertion.sub` claim in the OIDC token that GitHub provides.



## Step 7: Add GitHub Repository Secrets

Based on the values from the previous `echo` commands, add these as repository secrets in our GitHub repository (**Settings > Secrets and variables > Actions**):

- `GCP_PROJECT_ID`: Value of `${GCP_PROJECT_ID}` (e.g., `sdcote`)
- `GCP_WORKLOAD_IDENTITY_PROVIDER`: Value of `${WIF_PROVIDER_NAME}` (e.g., `projects/1234567890/locations/global/workloadIdentityPools/gha-pool-webservices/providers/github-sdcote-webservices`)
- `GCP_SERVICE_ACCOUNT_EMAIL`: Value of `${GCP_SERVICE_ACCOUNT_EMAIL}` (e.g., `github-actions-builder-deployer@sdcote.iam.gserviceaccount.com`)
- `GCP_ARTIFACT_REGISTRY_LOCATION`: Our Artifact Registry region (e.g., `us-east5`)
- `GCP_CLOUD_RUN_REGION`: Our Cloud Run region (e.g., `us-central1`)
- `CLOUD_RUN_SERVICE_NAME`: The name we want our Cloud Run service to have (e.g., `webservices-service`)

Once all these GCP steps are completed and the GitHub Secrets are set, our `build.yml` workflow should be able to successfully authenticate to GCP using Workload Identity Federation and execute the Jib build and push.



# Scratch pad



### Jib auth configuration

```
<!-- Optional: Set up authentication for CI/CD if not using gcloud defaults.
     For local development with 'gcloud auth configure-docker', this block might not be needed.
	 NOTE:  Otherwise, use the "auth" section to pass the 
	 _contents_ of the JSON key file or use Workload Identity Federation (WIF).
     NEVER hardcode credentials here. Use environment variables.
<auth>
	<username>_json_key</username>
	<password>${env.GOOGLE_BUILDER_KEY}</password>
</auth>
-->
```



[webservices/pom.xml at main · sdcote/webservices · GitHub](https://github.com/sdcote/webservices/blob/main/pom.xml)





The core problem is a persistent **authentication failure specific to Jib**, despite a fully validated and functional Google Cloud Workload Identity Federation (WIF) setup.

Here's a summary of what we believe the problem is:

**Problem Summary:**

Jib, when building and pushing a Docker image in a GitHub Actions workflow authenticated via Workload Identity Federation, consistently fails with an "Unauthenticated request" (403 Forbidden) to Google Artifact Registry.

**Key Observations & Contradictions:**

1. **Workload Identity Federation Works:** The `google-github-actions/auth` step successfully authenticates to Google Cloud. GCP audit logs show `sts.googleapis.com` `ExchangeToken` calls completing with `status: {}` (success), meaning the GitHub OIDC token is correctly exchanged for temporary Google Cloud credentials for the service account.
2. **GCP Service Account Permissions are Correct:** The `builder@sdcote.iam.gserviceaccount.com` service account has all necessary roles (e.g., `Artifact Registry Writer`, `Service Account Token Creator`, `Workload Identity User`). Local builds using this service account key work, and `gcloud auth print-access-token` *successfully generates an access token* on the GitHub Actions runner.
3. **Jib's Contradictory Behavior:**
   - Jib reports `[DEBUG] Google ADC found` (indicating it detects the presence of Application Default Credentials).
   - Immediately after, it reports `[DEBUG] ADC not present or error fetching access token: Error requesting access token`.
   - This contradiction suggests Jib finds an ADC setup but fails internally when attempting to *use* the temporary credentials file provided by `google-github-actions/auth` to fetch a live access token.
4. **Persistent Failure Across Methods/Versions:**
   - Relying on Jib's default ADC discovery (failed).
   - Explicitly pointing Jib to the temporary credential file path via `JIB_GOOGLE_CREDENTIALS_FILE` system property (failed, despite `mvn help:system` confirming the property was set).
   - Downgrading Jib to versions 3.3.0 and 3.2.0 (same error).

**Conclusion:**

The problem appears to be a **specific bug or incompatibility within Jib's Java-based Google Cloud client libraries** (or its integration with them) that prevents it from successfully loading or using the temporary credential file generated by `google-github-actions/auth` in the GitHub Actions runner environment. Despite the underlying Google Cloud authentication working perfectly with other `gcloud` components, Jib is unable to acquire the necessary access token for Artifact Registry operations.
