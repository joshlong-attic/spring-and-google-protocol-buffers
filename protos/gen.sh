#!/usr/bin/env bash


SRC_DIR=`pwd`
DST_DIR=`pwd`/../src/main/

echo source:            $SRC_DIR
echo destination root:  $DST_DIR

function ensure_implementations(){

    # Ruby and Go aren't natively supported it seems
    # Java and Python are

    # there are myriad other implementations:
    #   https://github.com/google/protobuf/wiki/Third-Party-Add-ons#ThirdParty_Addons_for_Protocol_Buffers
    gem list | grep ruby-protocol-buffers || sudo gem install ruby-protocol-buffers
    go get -u github.com/golang/protobuf/{proto,protoc-gen-go}
}

function gen(){
    D=$1
    echo $D
    OUT=$DST_DIR/$D
    mkdir -p $OUT
    protoc -I=$SRC_DIR --${D}_out=$OUT $SRC_DIR/customer.proto
}

ensure_implementations

gen java
gen python
gen ruby