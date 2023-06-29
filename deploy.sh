#!/usr/bin/env bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

LOG="$DIR/deploy.log"
filename=$(basename $0)
timestamp=$(date +"%m-%d-%Y_%H:%M:%S")
echo "$timestamp - Running $filename ... " | tee "$LOG"

source "${DIR}"/SET

if [[ -z "${API_DOMAIN}" ]]; then
  echo API_DOMAIN env variable is not set.  | tee -a "$LOG"
  exit
fi

if [[ -z "${PROJECT_ID}" ]]; then
  echo PROJECT_ID variable is not set. | tee -a "$LOG"
  exit
fi

gcloud container clusters get-credentials main-cluster --region $REGION --project $PROJECT_ID


VERSION=$(kustomize version)
if [[ "$VERSION" != *"v4.5.7"* ]]; then
  sudo rm /usr/local/bin/kustomize
  curl -Lo install_kustomize "https://raw.githubusercontent.com/kubernetes-sigs/kustomize/master/hack/install_kustomize.sh" && chmod +x install_kustomize
  sudo ./install_kustomize 4.5.7 /usr/local/bin
  kustomize version
fi

#create_cert(){
#  SVC_HOST=$1
#  NAME=tls-config
#
##  openssl req -new -newkey rsa:4096 -x509 -sha256 -days 365 -nodes -subj \
##      "/CN=${SVC_HOST}" \
##      -addext "subjectAltName = DNS:localhost,DNS:${SVC_HOST}" \
##      -out "tls.crt" -keyout "tls.key"
#
#  if kubectl get secrets --namespace="$KUBE_NAMESPACE" | grep $NAME; then
#    echo "$NAME exists in namespace $KUBE_NAMESPACE, skipping..."
#  else
#    echo "Creating self-signed certificate for host " "$SVC_HOST"
#    openssl req -new -newkey rsa:4096 -x509 -sha256 -days 365 -nodes -subj \
#        "/CN=auth.endpoints.rosy-resolver-348520.cloud.goog" \
#        -out "tls.crt" -keyout "tls.key"
#    kubectl create secret tls $NAME --cert=tls.crt  --key=tls.key --namespace="$KUBE_NAMESPACE"
#    rm tls.crt
#    rm tls.key
#  fi
#
#
#}

#KUBE_NAMESPACE="default"
#create_cert "$AUTH"

bash apply.sh
skaffold run -p prod --default-repo=gcr.io/${PROJECT_ID} | tee -a "$LOG"


timestamp=$(date +"%m-%d-%Y_%H:%M:%S")
echo "$timestamp Finished. Saved Log into $LOG"  | tee -a "$LOG"