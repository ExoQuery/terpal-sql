#!/bin/bash

./gradlew :controller-core:publishToMavenLocal \
          :controller-jdbc:publishToMavenLocal \
          :controller-android:publishToMavenLocal \
          :controller-native:publishToMavenLocal \
          :terpal-sql-core:publishToMavenLocal \
          :terpal-sql-jdbc:publishToMavenLocal \
          :terpal-sql-native:publishToMavenLocal \
          :terpal-sql-android:publishToMavenLocal
