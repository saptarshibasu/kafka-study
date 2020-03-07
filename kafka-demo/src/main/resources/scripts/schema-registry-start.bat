#!/bin/bash
#
# Copyright 2018 Confluent Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

SetLocal
IF ["%SCHEMA_REGISTRY_LOG4J_OPTS%"] EQU [""] (
    if exist %~dp0../../etc/schema-registry/log4j.properties (
        set SCHEMA_REGISTRY_LOG4J_OPTS=-Dlog4j.configuration=file:%~dp0../../etc/schema-registry/log4j.properties
    ) else (
        set SCHEMA_REGISTRY_LOG4J_OPTS=-Dlog4j.configuration=file:%~dp0../../config/log4j.properties
    )
)
IF ["%SCHEMA_REGISTRY_LOG4J_OPTS%"] EQU [""] (
    rem detect OS architecture
    wmic os get osarchitecture | find /i "32-bit" >nul 2>&1
    IF NOT ERRORLEVEL 1 (
        rem 32-bit OS
        set SCHEMA_REGISTRY_LOG4J_OPTS=-Xmx512M -Xms512M
    ) ELSE (
        rem 64-bit OS
        set SCHEMA_REGISTRY_LOG4J_OPTS=-Xmx1G -Xms1G
    )
)

"%~dp0schema-registry-run-class.bat" io.confluent.kafka.schemaregistry.rest.SchemaRegistryMain %*

EndLocal
