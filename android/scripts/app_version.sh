#!/bin/bash -x

./gradlew :app:properties --no-daemon -q | grep outreach.version
