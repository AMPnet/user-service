# Crowdfunding User Service

[![CircleCI](https://circleci.com/gh/AMPnet/user-service/tree/master.svg?style=svg&circle-token=684d2feb016487f9d13ef78300b118c9a16cd6fe)](https://circleci.com/gh/AMPnet/user-service/tree/master) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/bb8b7631446c434dba9aa04b3d554da6)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=AMPnet/ampnet-user-service&amp;utm_campaign=Badge_Grade) [![codecov](https://codecov.io/gh/AMPnet/user-service/branch/master/graph/badge.svg)](https://codecov.io/gh/AMPnet/user-service)

User service is a part of the AMPnet crowdfunding project. Service contains user data and generates JWT token used for user authentication to other crowdfunding services.
Using gRPC, service is connected to [mail service](https://github.com/AMPnet/mail-service).

## Requirements

Service must have running and initialized database. Default database url is `locahost:5432`.
To change database url set configuration: `spring.datasource.url` in file `application.properties`.
To initialize database run script in the project root folder:

```sh
./initialize-local-database.sh
```

## Start

Application is running on port: `8125`. To change default port set configuration: `server.port`.

### Build

```sh
./gradlew build
```

### Run

```sh
./gradlew bootRun
```

After starting the application, API documentation is available at: `localhost:8125/docs/index.html`.
If documentation is missing generate it by running gradle task:
```sh
./gradlew copyDocs
```

### Test

```sh
./gradlew test
```
