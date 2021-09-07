# GOV.UK Sign In APIs

There are 4 API modules:

- OIDC - Endpoints as defined by the OIDC specification for login flows
- Client Registry - Endpoints defined by the OIDC specification for the client registry
- Frontend API - The API used by the authorisation frontend to interact with the service
- Account Management API - The API used by the account management application to access user data

## Terraform

There are Terraform stacks for different parts of the API these are used to deploy the application to AWS:

- `ci/terraform/oidc` - Terraforms the OIDC, account management and frontend APIs, including the Dynamo data stores.
- `ci/terraform/account-management` - Terraforms the account management API (this is dependent on the Dynamo data stores in the OIDC Terraform)

### Testing Terraform with the `sandpit`

A developer sandpit environment exists to test the Terraform against.

Firstly, authenticate to AWS, you can easily do this the the GDS CLI:

```bash
eval $(gds aws digital-identity-dev -e)
```

To deploy to sandpit, do the following:

```bash
cd ci/terraform/<oidc|account-management>
rm -rf .terraform/ # This tidies any data from historic deploys to other environments, e.g. localstack
terraform init -backend-config=sandpit.hcl
terraform plan -var-file=sandpit.tfvars -out sandpit.plan
terraform apply sandpit.plan
```
