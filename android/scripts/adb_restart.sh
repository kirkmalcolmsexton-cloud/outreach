#!/bin/bash -x

adb devices
adb kill-server
adb start-server
adb devices
