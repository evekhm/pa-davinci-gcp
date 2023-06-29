#!/usr/bin/env bash
set -e # Exit if error is detected during pipeline execution
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PWD=$(pwd)
source "$DIR"/SET

echo "$(basename "$0") BUCKET=$BUCKET"

### crd
cd "$DIR/microservices/crd/kustomize/k8s"
sed 's|__PROJECT_ID__|'"$PROJECT_ID"'|g;
      s|__BUCKET__|'"$BUCKET"'|g;
      s|__DB__|'"$DB_NAME"'|g;
      s|__VSAC_API_KEY__|'"$VSAC_API_KEY"'|g;
      s|__CRD_CONFIG__|'"$CRD_CONFIG"'|g;
      s|__DTR__|'"$DTR"'|g;
      s|__AUTH__|'"$AUTH"'|g;
      s|__CRD_REQUEST_GENERATOR__|'"$CRD_REQUEST_GENERATOR"'|g;
      s|__TEST_EHR__|'"$TEST_EHR"'|g; ' config.sample.yaml > "$DIR/microservices/crd/kustomize/base/config.yaml"

cd "$PWD" || exit


## crd-request-generator
cd "$DIR/microservices/crd-request-generator/kustomize/k8s"
sed 's|__FHIR_SERVER__|'"$FHIR_SERVER"'|g;
    s|__AUTH__|'"$AUTH"'|g;
    s|__PUBLIC_KEYS__|'"$PUBLIC_KEYS"'|g;
    s|__DTR__|'"$DTR"'|g;
    s|__CRD_REQUEST_GENERATOR_CONFIG__|'"$CRD_REQUEST_GENERATOR_CONFIG"'|g;
    s|__ORDER_SELECT__|'"$ORDER_SELECT"'|g;
    s|__ORDER_SIGN__|'"$ORDER_SIGN"'|g;
    s|__CDS_SERVICE__|'"$CDS_SERVICE"'|g;' config.sample.yaml > "$DIR/microservices/crd-request-generator/kustomize/base/config.yaml"


#dtr
cd "$DIR/microservices/dtr/kustomize/k8s"
sed 's|__PRIOR_AUTH__|'"$PRIOR_AUTH"'|g;
     s|__TEST_EHR__|'"$TEST_EHR"'|g; ' config.sample.yaml > "$DIR/microservices/dtr/kustomize/base/config.yaml"
cd "$PWD" || exit


#test-ehr
cd "$DIR/microservices/test-ehr/kustomize/k8s"
sed 's|__AUTH__|'"$AUTH"'|g; s|__TEST_EHR__|'"$TEST_EHR"'|g; ' config.sample.yaml >  "$DIR/microservices/test-ehr/kustomize/base/config.yaml"

##prior-auth
cd "$DIR/microservices/prior-auth/kustomize/k8s"
sed 's|__PROJECT_ID__|'"$PROJECT_ID"'|g;
      s|__BUCKET__|'"$BUCKET_NAME"'|g;
      s|__PRIOR_AUTH__|'"$PRIOR_AUTH"'|g;
      s|__DB__|'"$DB_NAME"'|g; ' config.sample.yaml >  "$DIR/microservices/prior-auth/kustomize/base/config.yaml"