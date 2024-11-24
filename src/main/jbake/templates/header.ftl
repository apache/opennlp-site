<!DOCTYPE html>
<#--
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
<#setting date_format="dd-MM-yyyy">
<#setting locale="en_US">
<#setting time_zone="GMT">
<html lang="en">
<head>
    <meta charset="utf-8">
    <title><#if (content.title)??><#escape x as x?xml>${content.title} - Apache OpenNLP</#escape><#else>Apache OpenNLP</#if></title>
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="dark light">
    <meta name="description" content="Apache OpenNLP is a machine learning based toolkit for the processing of natural language text." />
    <meta name="author" content="The Apache OpenNLP Team" />
    <meta name="keywords" content="java, natural language processing, nlp, apache, open source, web site" />
    <meta name="generator" content="JBake"/>
    
    <!-- RSS Feed -->
    <link rel="alternate" type="application/rss+xml" title="RSS" href="/${config.feed_file}" />

    <!-- Favicon -->
    <link rel="apple-touch-icon" sizes="57x57" href="/apple-icon-57x57.png">
    <link rel="apple-touch-icon" sizes="60x60" href="/apple-icon-60x60.png">
    <link rel="apple-touch-icon" sizes="72x72" href="/apple-icon-72x72.png">
    <link rel="apple-touch-icon" sizes="76x76" href="/apple-icon-76x76.png">
    <link rel="apple-touch-icon" sizes="114x114" href="/apple-icon-114x114.png">
    <link rel="apple-touch-icon" sizes="120x120" href="/apple-icon-120x120.png">
    <link rel="apple-touch-icon" sizes="144x144" href="/apple-icon-144x144.png">
    <link rel="apple-touch-icon" sizes="152x152" href="/apple-icon-152x152.png">
    <link rel="apple-touch-icon" sizes="180x180" href="/apple-icon-180x180.png">
    <link rel="icon" type="image/png" sizes="192x192"  href="/android-icon-192x192.png">
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
    <link rel="icon" type="image/png" sizes="96x96" href="/favicon-96x96.png">
    <link rel="icon" type="image/png" sizes="16x16" href="/favicon-16x16.png">
    <link rel="manifest" href="/manifest.json">
    <meta name="msapplication-TileColor" content="#ffffff">
    <meta name="msapplication-TileImage" content="/ms-icon-144x144.png">
    <meta name="theme-color" content="#ffffff">

    <!-- The styles -->
    <link href="/css/bootstrap.min.css" rel="stylesheet"/>
    <link href="/css/font-awesome.min.css" rel="stylesheet"/>
    <link href="/css/asciidoctor.css" rel="stylesheet"/>
    <link href="/css/prettify.css" rel="stylesheet"/>
    <link href="/css/custom-style.css" rel="stylesheet"/>
    <link href="/css/scheme-light.css" rel="stylesheet"/>
    <link href="/css/scheme-dark.css" rel="stylesheet"/>
    <link href="/css/shareon-2.6.0.min.css" rel="stylesheet"/>

    <!-- HTML5 shim, for IE6-8 support of HTML5 elements -->
    <!--[if lt IE 9]>
      <script src="/js/html5shiv.min.js"></script>
    <![endif]-->
</head>
<body onload="prettyPrint()">

<!-- Include pure CSS/HTML5 ForkMeOnGH ribbon -->
<span id="forkongithub"><a href="https://github.com/apache/opennlp">Fork me on GitHub</a></span>