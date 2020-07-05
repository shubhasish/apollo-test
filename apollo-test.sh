#!/usr/bin/env bash

set -eou pipefail


function help() {
    echo "Refer Readme.md"
}

function create-infra() {

echo "Creating Infra"

terraform init
terraform apply
}

function deploy-nginx() {
    echo "Initiating backend"
    terraform init
     echo
     echo
     echo "Deploying nginx"
     ansible-playbook -i host/terraform.py main.yaml
}

if [ "$#" -lt 1 ]
 then
 help
 exit 0
fi

case ${1} in

create-infra)
shift
create_infra
shift
;;
deploy-nginx)
shift
deploy_nginx
shift
;;
help)
shift
help
shift
;;
*)
help
shift
;;
esac