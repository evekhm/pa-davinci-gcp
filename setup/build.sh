
build(){
  app=$1
  cd "/microservices/${app}" || exit
  gcloud builds submit --region=$REGION --substitutions=_IMAGE="${app}"
}

build crd
build crd-request-generator
build dtr
build prior-auth
build test-ehr