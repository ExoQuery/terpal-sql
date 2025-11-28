#!/bin/bash

./gradlew :controller-core:publishToMavenLocal \
          :controller-jdbc:publishToMavenLocal \
          :controller-android:publishToMavenLocal \
          :controller-native:publishToMavenLocal \
          :controller-r2dbc:publishToMavenLocal \
          :terpal-sql-core:publishToMavenLocal \
          :terpal-sql-jdbc:publishToMavenLocal \
          :terpal-sql-native:publishToMavenLocal \
          :terpal-sql-android:publishToMavenLocal \
          :terpal-sql-r2dbc:publishToMavenLocal
