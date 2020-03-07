REM !/bin/bash
REM 
REM  Copyright 2018 Confluent Inc.
REM 
REM  Licensed under the Apache License, Version 2.0 (the "License");
REM  you may not use this file except in compliance with the License.
REM  You may obtain a copy of the License at
REM 
REM  http://www.apache.org/licenses/LICENSE-2.0
REM 
REM  Unless required by applicable law or agreed to in writing, software
REM  distributed under the License is distributed on an "AS IS" BASIS,
REM  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM  See the License for the specific language governing permissions and
REM  limitations under the License.
REM 

setlocal enabledelayedexpansion

IF [%1] EQU [] (
	echo USAGE: $0 [-daemon] [-name servicename] [-loggc] classname [opts]
	EXIT /B 1
)

rem Using pushd popd to set BASE_DIR to the absolute path
pushd %~dp0..\..
set BASE_DIR=%CD%
popd

set CLASSPATH=$CLASSPATH;%BASE_DIR%\share\java\schema-registry\*;%BASE_DIR%\share\java\confluent-security\schema-registry\*;%BASE_DIR%\share\java\confluent-common\*;%BASE_DIR%\share\java\rest-utils\*

rem Log directory to use
IF ["%LOG_DIR%"] EQU [""] (
    set LOG_DIR=%BASE_DIR%/logs
)

rem Log4j settings
IF ["%SCHEMA_REGISTRY_LOG4J_OPTS%"] EQU [""] (
	if exist %~dp0../../etc/schema-registry/log4j.properties (
		set SCHEMA_REGISTRY_LOG4J_OPTS=-Dlog4j.configuration=file:%~dp0../../schema-registry/log4j.properties
	) else (
		set SCHEMA_REGISTRY_LOG4J_OPTS=-Dlog4j.configuration=file:%BASE_DIR%/config/log4j.properties
	)
) ELSE (
  rem create logs directory
  IF not exist "%LOG_DIR%" (
      mkdir "%LOG_DIR%"
  )
)

set SCHEMA_REGISTRY_LOG4J_OPTS=-Dschema-registry.log.dir="%LOG_DIR%" "%SCHEMA_REGISTRY_LOG4J_OPTS%"

rem Generic jvm settings you want to add
IF ["%SCHEMA_REGISTRY_OPTS%"] EQU [""] (
	set SCHEMA_REGISTRY_OPTS=
)

IF ["%JAVA_HOME%"] EQU [""] (
	set JAVA=java
) ELSE (
	set JAVA="%JAVA_HOME%/bin/java"
)

rem Memory options
IF ["%SCHEMA_REGISTRY_HEAP_OPTS%"] EQU [""] (
	set SCHEMA_REGISTRY_HEAP_OPTS=-Xmx512M
)

rem JVM performance options
IF ["%SCHEMA_REGISTRY_JVM_PERFORMANCE_OPTS%"] EQU [""] (
	set SCHEMA_REGISTRY_JVM_PERFORMANCE_OPTS=-server -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:InitiatingHeapOccupancyPercent=35 -XX:+ExplicitGCInvokesConcurrent -Djava.awt.headless=true
)

set COMMAND=%JAVA% %SCHEMA_REGISTRY_HEAP_OPTS% %SCHEMA_REGISTRY_JVM_PERFORMANCE_OPTS% %SCHEMA_REGISTRY_JMX_OPTS% %SCHEMA_REGISTRY_LOG4J_OPTS% -cp "%CLASSPATH%" %SCHEMA_REGISTRY_OPTS% %*
rem echo.
rem echo %COMMAND%
rem echo.
%COMMAND%
