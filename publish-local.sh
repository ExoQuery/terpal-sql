#!/bin/bash

./gradlew :terpal-sql-core:publishToMavenLocal \
          :terpal-sql-jdbc:publishToMavenLocal \
          :terpal-sql-native:publishToMavenLocal \
          :terpal-sql-android:publishToMavenLocal
