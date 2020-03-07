start bin\windows\zookeeper-server-start.bat etc\kafka\zookeeper.properties

TIMEOUT 5

start bin\windows\kafka-server-start.bat etc\kafka\server.properties

TIMEOUT 5

start bin\windows\schema-registry-start.bat etc\schema-registry\schema-registry.properties
