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

[![Build Status](https://api.travis-ci.org/apache/opennlp-site.svg?branch=master)](https://travis-ci.org/apache/opennlp-site)
[![GitHub license](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://raw.githubusercontent.com/apache/opennlp/master/LICENSE)
[![Twitter Follow](https://img.shields.io/twitter/follow/ApacheOpennlp.svg?style=social)](https://twitter.com/ApacheOpenNLP)

#### Build

`mvn clean install`

#### Test Site locally - starts a web server on Port 8080
`mvn clean package jbake:inline -Djbake.port=8080 -Djbake.listenAddress=0.0.0.0`

