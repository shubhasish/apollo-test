#Apollo Test

### Creating Infra
To create an infra first modify the `terraform.tfvars` file. Insert the appropriate values for the keys.

Run `./apollo-test.sh create-infra`

This will create the VM, VPC, subnet, firewall, and a GKE cluster.


### Deploying ansible
This script uses [terraform ansible provider](https://nicholasbering.ca/tools/2018/01/08/introducing-terraform-provider-ansible/#:~:text=The%20logical%20provider%20creates%20an,from%20Terraform%20state%20to%20files.).

Please download and setup the plugins in your `~/.terraform.d/plugins/` folder.

After that run `./apollo-test.sh deploy-nginx`

This will deploy a nginx in the VM created in the previous step. Deploy a [hello-world application](https://github.com/shubhasish/hello-world).

Clone the [hello-world repo](https://github.com/shubhasish/hello-world).
Dockerfile is present inside that repo itself. The docker image has been built and put in the [docker hub repository](https://hub.docker.com/repository/docker/shubhashish/helloworld).
If you still want to build your docker image, go inside helloworld folder and run `docker build -t <whatever-tag> .` and push the docker image to a public repo with command `docker push <whatever-tag>`.

Now update the docker-image name in `terraform.tfvars` and run `terraform apply` before deploying the nginx using `./apollo-test.sh deploy-nginx`

### Deploying into K8s

Clone the [hello-world repo](https://github.com/shubhasish/hello-world) as mentioned above.

Now the gke is create when we ran `./apollo-test.sh create-infra`. So, we just need the kube config file.

To do that install [gcloud](https://cloud.google.com/sdk/install).

Run `gcloud init`. This will ask you to login into your google account and set your default project.

Now set the kube config by running `gcloud container clusters get-credentials <cluster-name> --region <cluster-region> --project <gcp-project>`.

Now once the kubeconfig is initialized. Just go inside the hello-world folder and run `helm upgrade --install helloworld ./helloworld`.

You can install helm using this [doc](https://helm.sh/docs/intro/install/).
