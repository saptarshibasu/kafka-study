# Kafka Study

**Kafka Latest Version - 2.4.0**

## Commands

```
bin\windows\zookeeper-server-start.bat config\zookeeper.properties
```
```
bin\windows\kafka-server-start.bat config\server.properties
```
```
bin\windows\kafka-topics.bat --create --zookeeper localhost:2181 --replication-factor 1 --partitions 2 --topic word-count-input
```
```
bin\windows\kafka-console-producer.bat --broker-list localhost:9092 --topic word-count-input
```
```
bin\windows\kafka-console-consumer.bat --bootstrap-server localhost:9092 ^
    --topic word-count-output ^
    --from-beginning ^
    --formatter kafka.tools.DefaultMessageFormatter ^
    --property print.key=true ^
    --property print.value=true ^
    --property key.deserializer=org.apache.kafka.common.serialization.StringDeserializer ^
    --property value.deserializer=org.apache.kafka.common.serialization.LongDeserializer
```
```
bin\windows\kafka-topics.bat --delete --zookeeper localhost:2181 --topic word-count-input
```
```
bin\windows\kafka-topics.bat --list --zookeeper localhost:2181
```

## Basics

* Kafka is a distributed, partitioned, replicated commit log service
* Data is organized among topics
* Each topic is partitioned
* Each partition can have multiple replicas
* Each broker hosts hundreds or thousands of replicas belonging to different topics
* Replication factor must be less than or equal to the number of brokers up and running
* Kafka recommends using replication for durability and not set the disk flush parameters
* Two types of replicas
  * Leader replica - 
    * Each partition has a leader replica
    * All producer & consumer requests go through the leader replica to guarantee consistencies
    * Leaders keep track of the last offsets fetched by each replica
    * Preferred leader - The replica that was the leader when the topic was original created
    * When the preferred leader is indeed the leader, the load is expected to be more evenly distributed among the brokers
    * By default, Kafka is configured with auto.leader.rebalance.enable=true, which will check if the preferred leader replica is not the current leader but is in-sync and trigger leader election to make the preferred leader the current leader
  * Follower replica -
    * All other replicas are followers
    * In the event of leader replica failure, one of the follower replicas will be promoted as leader
    * Followers don't serve client request
    * Their only job is to replicate messages from the leader and stay in sync with the leader
    * Only in-sync replicas are eligible to be elected as leader in the event of leader failure
* Insync replica conditions - 
  * It is a leader, or
  * It is a follower replica, and
    * It has sent a heartbeat to Zookeeper in last 6 seconds
    * It has requested message from the leader within last 10 seconds
    * It has fetched the most recent messages from the leader in the last 10 seconds (replica.lag.time.max.ms)
* Kafka uses ZooKeeper to maintain the list of brokers that are currently members of a cluster
* Controller is a broker that is responsible for electing partition leader (in addition to regular broker responsibilities)
* The first broker that starts in the cluster will become the controller
* If the controller goes down, another broker will become the controller with a higher epoch number and thus preventing "split brain" from occuring
* Producer Properties
  * acks=0
    * The producer will not wait for reply from the broker
    * Message loss is possible
    * Throughput is high as the producer doesn't have to wait for response from the broker
  * acks=1
    * The producer will receive a response the moment the leader replica receives the message
    * If the message can't be written to the leader, the producer will receive an error and will retry automatically
    * Message loss is possible in the event of unclean leader election (by default disabled)
    * If the message is sent synchronously, the latency will be high
    * If the message is sent asynchronously, the latency will be hidden but the throughput depends on the number of allowed inflight messages before receiving a response from the server)
  * acks=all
    * The producer will receive a success response after all the in-sync replicas received the message
    * Message is durable
    * Latency is high
* Client
    * Must send the fetch requests to the leader replica of a given partition (otherwise "Not a leader" error is returned)
    * Knows about the replica and broker details for a given topic using metadata requests
    * Cliemts cache the metadata information
    * Clients fetch metadata information when metadata.max.age.ms expires or "Not a leader" error is returned (partition leader moved to a different broker due to failure)
* As Kafka writes its data to local disk, the data may linger in the filesystem cache and may not make its way to the disk. Therefore Kafka relies solely on replication for reliability
* Kafka consumer stores the last offset read in a special Kafka topic
* Segments
  * Each partition is split into multiple segments
  * By default, each segment contains 1 GB of data or 1 week worth of data whichever is smaller
  * If the segment limit is reached, the segment file is closed and a new segment file is created
  * Kafka brokers always keeps an open file handle to each segment of each partition
  * The segment being currently writtent to for a given partition is called active segment
  * Active segments are never deleted
  * If the retention policy is set as "delete", the old segments are deleted depending 
* Kafka Guarantees - 
  * Kafka provides order guarantee of messages in a partition
  * Produced messages are considered "committed" when they were written to the partition on all its in-sync replicas
  * Messages that are committed will not be losts as long as at least one replica remains alive
  * Consumers can only read messages that are committed
* Message Reliability (Typical Scenario) - 
  * Replication Factor = 3
  * min.insync.replicas = 2
  * producer acks = all

## Kafka Streams

* StreamsConfig.APPLICATION_ID_CONFIG (application.id) -
  * Mandatory
  * Consumer group id
  * Internal topic name prefix
  * Client id prefix
* ConsumerConfig.AUTO_OFFSET_RESET_CONFIG (auto.offset.reset) - 
  * It's applicable when the consumer group doesn't have any offset associated in Kafka
  * The possible values are - earliest (read the messages from the begining), latest (read the new messages)
