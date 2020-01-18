#!/usr/bin/env bash

## Clone
git clone https://github.com/devgianlu/aria2-android
cd aria2-android

## Prepare env
export SILENT=true

## Do stuff
./build_all.sh

## Check the result
file ./bin/armeabi-v7a/bin/aria2c
file ./bin/arm64-v8a/bin/aria2c
file ./bin/armeabi-x86/bin/aria2c
file ./bin/armeabi-x86_64/bin/aria2c