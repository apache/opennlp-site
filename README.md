<!--
   Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements.  See the NOTICE file
   distributed with this work for additional information
   regarding copyright ownership.  The ASF licenses this file
   to you under the Apache License, Version 2.0 (the
   "License"); you may not use this file except in compliance
   with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing,
   software distributed under the License is distributed on an
   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   KIND, either express or implied.  See the License for the
   specific language governing permissions and limitations
   under the License.  
-->

Welcome to OpenNLP Site Source Code
====================================

[![GitHub license](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://raw.githubusercontent.com/apache/opennlp/main/LICENSE)
[![Build Status](https://github.com/apache/opennlp/workflows/Java%20CI/badge.svg)](https://github.com/apache/opennlp-site/actions)
[![Stack Overflow](https://img.shields.io/badge/stack%20overflow-opennlp-f1eefe.svg)](https://stackoverflow.com/questions/tagged/opennlp)

#### Build

```bash
mvn clean install
```

#### Test Site locally - starts a web server on Port 8080
```bash
mvn clean package jbake:inline -Djbake.port=8080 -Djbake.listenAddress=0.0.0.0
```
#### Build Bot

Website is build via ASF BuildBot. You find it [here](https://ci.apache.org/).
