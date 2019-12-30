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

## Kafka Basics

* Kafka is a **distributed, partitioned, replicated** commit log service
* Data is organized among topics
* Each topic is partitioned
* Each partition can have multiple replicas
* Each broker hosts hundreds or thousands of replicas belonging to different topics
* Replication factor must be less than or equal to the number of brokers up and running
* Kafka recommends using replication for durability and not set the disk flush parameters
* As Kafka writes its data to local disk, the data may linger in the filesystem cache and may not make its way to the disk. Therefore Kafka relies solely on replication for reliability
* Two types of replicas
  * **Leader replica** - 
    * Each partition has a leader replica
    * All producer & consumer requests go through the leader replica to guarantee consistencies
    * Leaders keep track of the last offsets fetched by each replica
    * Preferred leader - The replica that was the leader when the topic was original created
    * When the preferred leader is indeed the leader, the load is expected to be more evenly distributed among the brokers
    * By default, Kafka is configured with `auto.leader.rebalance.enable=true`, which will check if the preferred leader replica is not the current leader and trigger leader election to make the preferred leader the current leader if it is in-sync 
  * **Follower replica** -
    * All other replicas are followers
    * In the event of leader replica failure, one of the follower replicas will be promoted as leader
    * Followers don't serve client request
    * Their only job is to replicate messages from the leader and stay in sync with the leader
    * Only in-sync replicas are eligible to be elected as leader in the event of leader failure
* **Insync replica conditions** - 
  * It is a leader, or
  * It is a follower replica, and
    * It has sent a heartbeat to Zookeeper in last 6 seconds
    * It has requested message from the leader within last 10 seconds
    * It has fetched the most recent messages from the leader in the last 10 seconds (replica.lag.time.max.ms)
* Kafka uses ZooKeeper to maintain the list of brokers that are currently members of a cluster
* **Controller** is a broker that is responsible for electing partition leader (in addition to regular broker responsibilities)
* The first broker that starts in the cluster will become the controller
* If the controller goes down, another broker will become the controller with a higher epoch number and thus preventing "split brain" from occuring
* **Client**
    * Must send the fetch requests to the leader replica of a given partition (otherwise "Not a leader" error is returned)
    * Knows about the replica and broker details for a given topic using metadata requests
    * Cliemts cache the metadata information
    * Clients fetch metadata information when metadata.max.age.ms expires or "Not a leader" error is returned (partition leader moved to a different broker due to failure)
    * Metadata requests can be sent to any broker because all brokers cache this information
* Kafka consumer stores the last offset read in a special Kafka topic
* ZooKeeper stores the broker metadata
* **Segments**
  * Each partition is split into multiple segments
  * By default, each segment contains 1 GB of data or 1 week worth of data whichever is smaller
  * If the segment limit is reached, the segment file is closed and a new segment file is created
  * Kafka brokers always keeps an open file handle to the active segment of each partition
  * The segment being currently writtent to for a given partition is called active segment
  * Active segments are never deleted
  * If the retention policy is set as "delete", the old segments are deleted depending
  * If both `log.segment.ms` & `log.segment.bytes` are set, a new segment will be created when either of these two criteria is met
  * Smaller log segments meaans frequest closing and allocation of files reducing overall disk performance
* **Deciding Number of Partitions**
  * Avoid underestimating as one partition will always be read by only one consumer
  * Avoid overestimating as each partition uses memory and other resources on a broker and increases the leader election time
* **Log Retention**
  * Default log retention by time is 7 days (`log.retention.ms`)
  * Retention by time is done based on the last modification time of the segment file (which is usually the last time a message is added to the segment unless some admistrative activities moved the partitions across brokers resulting is excess retention)
  * Default log retention by size is 1 GB (`log.retention.bytes`). This configuration is applicable per partition and NOT per topic
  * `log.retention.ms`, `log.retention.minutes` & `log.retention.hours` - If more than one of these parameters are set, the smallest unit takes precedence
  * If both log retention by size and time are configured, messages will be removed when either criteria is met
* **Kafka Guarantees**
  * Kafka provides order guarantee of messages in a partition
  * Produced messages are considered "committed" when they were written to the partition on all its in-sync replicas
  * Messages that are committed will not be losts as long as at least one replica remains alive
  * Consumers can only read messages that are committed
* **Message Reliability (Typical Scenario)**
  * Replication Factor = 3
  * min.insync.replicas = 2 (Consumers won't get message until the message is committed i.e. all in-sync replicas are updated)
  * producer acks = all (Producers won't get acknowledgement until all the in-sync replicas are updated)
* **Message Size**
  * `message.max.bytes` defaults to 1MB
  * `message.max.bytes` indicates the compressed message size
  * `fetch.message.max.bytes` (consumer) & `replica.fetch.max.bytes` must match with `message.max.bytes`
  * Larger `message.max.bytes` will impact disk I/O throughput
* **Hardware & OS**
  * Kafka brokers with a larger page cache helps in better consumer client performnce provided the consumers are lagging only a little from the producer. This ensures that the broker doen't need to reread the messages from the disk. Therefore, it is recommended not to run any other application in the broker host
  * On AWS, for lower latency I/O optimized instances will be good
  * Extents File System (XFS) & ZFS perform well for Kafka Workload
  * the mountpoints should have `noatime` option set to eliminate the 
  * export KAFKA_JVM_PERFORMANCE_OPTS="-server -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:InitiatingHeapOccupancyPercent=35 -XX:+DisableExplicitGC -Djava.awt.headless=true"
  * `vm.swappiness = 1` - A low value means the kernel will try to avoid swapping as much as possible making less memory available for page cache
  * `vm.dirty_background_ratio = 5` (default - 10) - The percentage of system memory which when dirty, system will start writing to disk
  * `vm.dirty_ratio = 60` (default - 15) - The percentage of memory which when dirty, the process doing writes would block and write out dirty pages to the disks
  * Tuning `vm.dirty_background_ratio` and `vm.dirty_ratio` as above relies on:
    * Good disk I/O performance (SSD or RAID)
    * Replication is used in the cluster to guard against system failure
  * `net.core.wmem_default = 131072` (128 KiB)
  * `net.core.rmem_default = 131072` (128 KiB)
  * `net.core.wmem_max = 131072` (128 KiB)
  * `net.core.rmem_max = 2097152` (2 MiB)
  * `net.ipv4.tcp_wmem = 4096 65536 2048000` (4 KiB 64 KiB 2 MiB)
  * `net.ipv4.tcp_rmem = 4096 65536 2048000` (4 KiB 64 KiB 2 MiB)


## Kafka Producers


* `acks=0`
  * The producer will not wait for reply from the broker
  * Message loss is possible
  * Throughput is high as the producer doesn't have to wait for response from the broker
* `acks=1`
  * The producer will receive a response the moment the leader replica receives the message
  * If the message can't be written to the leader, the producer will receive an error and will retry automatically
  * Message loss is possible in the event of unclean leader election (by default disabled)
  * If the message is sent synchronously, the latency will be high
  * If the message is sent asynchronously, the latency will be hidden but the throughput depends on the number of allowed inflight messages before receiving a response from the server)
* `acks=all`
  * The producer will receive a success response after all the in-sync replicas received the message
  * Message is durable
  * Latency is high
* `bootstrap.servers` - List host:port of brokers. All brokers need not be present and the producer will discover about other brokers from metadata. However, more than one broker should be specified so that the producer can connect even in the event of a broker failure
* `key.serializer`
* `value.serializer`
* Kafka producer object can be used by multiple threads. Once we reach a state where adding more threads do not improve performance, new producer object can be created
* `buffer.memory` - The memory buffer that will store the messages before being sent out to a broker. If the buffer runs out of space, the thread will remain blocked for `max.block.ms` before throwing an exception
* `compression.type` - By default messages are uncompressed. Supported compression algorithms - `gzip`, `snappy`, `lz4` and `zstd`
* `retries` - In case of retriable errors, the producer will retry these many times at an interval of `retry.backoff.ms`
* `batch.size` - When multiple records are sent to the same partition, the producer will batch them together. This parameter controls the amount of memory in bytes (not messages!) that will be used for each batch

## Kafka Streams

* StreamsConfig.APPLICATION_ID_CONFIG (application.id) -
  * Mandatory
  * Consumer group id
  * Internal topic name prefix
  * Client id prefix
* ConsumerConfig.AUTO_OFFSET_RESET_CONFIG (auto.offset.reset) - 
  * It's applicable when the consumer group doesn't have any offset associated in Kafka
  * The possible values are - earliest (read the messages from the begining), latest (read the new messages)

## ZooKeeper Basics

* A Zookeeper cluster is called an ensemble
* Due to the algorithm used, it is recommended that ensembles contain an odd number of servers (e.g., 3, 5, etc.)
* A three-node ensemble can run with one node missing
* A five-node ensemble can run with two nodes missing
* It's a good idea to run Zookeeper in a five-node ensemble so that the ensemble can run even when one node goes down while another node is taken down due to configuration change etc.

## Configurations To Be Changed For Production

Parameter | Description
--------- | -------------
`auto.create.topics.enable` | To be set as `false` so that topics don't get automatically created
`zookeeper.connect` | It is a good idea to specify a chroot in Kafka broker configuration. This allows the use of the same Zookeepr ensemble for other Kafka clusters
`min.insync.replicas` | Must be set to more than 1 for reliability
`unclean.leader.election.enable` | Should be kept to default `false` for reliability

## References

* Narkhede, Neha. Kafka: The Definitive Guide . O'Reilly Media. Kindle Edition
