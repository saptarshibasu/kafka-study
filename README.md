# Kafka Study

**Kafka Latest Version - 2.4.0**

- [Commands](#commands)

- [Kafka Basics](#kafka-basics)

  - [Introduction](#introduction)

  - [Performance](#performance)

  - [Replication](#replication)

  - [Retention](#retention)

  - [Delivery Symantics](#delivery-symantics)

- [Kafka Producers](#kafka-producers)

- [Kafka Consumers](#kafka-consumers)

- [Kafka Streams](#kafka-streams)

- [Kafka Connect](#kafka-connect)

- [Miscellaneous](#kafka-miscellaneous)

  - [Hardware & OS](#hardware-os)

  - [ZooKeeper Basics](#zooKeeper-basics)

  - [Avro Basics](#avro-basics)

- [References](#references)


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

### Introduction

* Kafka is a **distributed, partitioned, replicated** commit log service
* Data is organized among **topics**
* Each topic is **partitioned**
* Partitions are evenly distributed across the available brokers to distribute the load evenly
* Typically, there will be many more partitions than servers or brokers
* Partitions serves two purposes -
  * Allow storing more records a single machine can hold
  * Server as a unit of parallelism
* Kafka's performance is effectively constant with respect to data size so storing data for a long time is not a problem
* Producers and Consumers are Kafka clients
* Kafka only provides a total order over records within a partition, not between different partitions in a topic
* As Kafka writes its data to local disk, the data may linger in the filesystem cache and may not make its way to the disk. The disk flush parameter are not recommended to set for performance reasons. Therefore Kafka relies solely on replication for reliability
* In Linux, data written to the filesystem is maintained in pagecache until it must be written out to disk (due to an application-level fsync or the OS's ownflush policy)
* **Kafka Guarantees**
  * Kafka provides order guarantee of messages in a partition
  * Produced messages are considered "committed" when they were written to the partition on all its in-sync replicas
  * Messages that are committed will not be losts as long as at least one replica remains alive
  * Consumers can only read messages that are committed
* **ZooKeeper holds**
  * Dynamic per-broker or cluster-wide default config (includes encrypted passwords where secrets used for encoding password is kept in server.properties)
  * client quota configuration
  * Brokers register themselves in ZooKeeper using ephemeral znodes
  * Leaders of partitions of topics
  * Delegation tokens
  * ACLs
* **Internal topics**
  * __transaction_state
  * __consumer_offsets
* * **Message Reliability (Typical Scenario)**
  * Replication Factor = 3
  * min.insync.replicas = 2 (Consumers won't get message until the message is committed i.e. all in-sync replicas are updated)
  * producer acks = all (Producers won't get acknowledgement until all the in-sync replicas are updated)
* **Message Size**
  * `message.max.bytes` defaults to 1MB
  * `message.max.bytes` indicates the compressed message size
  * `fetch.message.max.bytes` (consumer) & `replica.fetch.max.bytes` must match with `message.max.bytes`
  * Larger `message.max.bytes` will impact disk I/O throughput


### Performance

* Modern operating systems are designed to aggressively use the available memory as page cache. Thus if the server hosting Kafka broker is not used for other applications, more page cache will be available for Kafka
* When consumers are lagging only a little from the producer, the broker doesn't need to reread the messages from the disk. The requests can be served directly from the page cache
* Compact byte structure rather than Java objects reduces Java Object ovehead
* Not having an in-process cache for messages makes more memory available for page cache & reduces garbage collection issues with increased in-heap data
* Simple reads and appends to file resulting in sequential disk access
* Transfer batches of messages over the network to amortize the network roundtrip, do larger sequential disk access, allocate contiguous memory blocks, provide good compression ratio
* Zero-copy - `sendfile` system call of Linux reduces byte copying (across kernel and user spaces) and context switches
* Standardized binary format shared by producer, broker & consumer reduces recopying & transformation
* Compression of message batch saves network bandwidth
* No intervening routing tier. Messages of a given partition are sent directly to the partition leader
* Consumers use "long poll" to avoid busy waiting and ensure larger transfer sizes

### Replication

* Each partition can have multiple replicas
* Each broker hosts hundreds or thousands of replicas belonging to different topics
* Replication factor must be less than or equal to the number of brokers up and running
* Kafka recommends using replication for durability and not set the disk flush parameters
* Each partition has a leader replica
* All producer & consumer requests go through the leader replica to guarantee consistencies
* Leaders keep track of the last offsets fetched by each replica
* For each partition, Kafka stores in Zookeeper the current leader and the current In-sync replicas (ISR)
* Preferred leader - The replica that was the leader when the topic was originally created
* When the preferred leader is indeed the leader, the load is expected to be more evenly distributed among the brokers
* By default, Kafka is configured with `auto.leader.rebalance.enable=true`, which will check if the preferred leader replica the current leader. If not and if it is ISR, a leader election will be triggered to make the preferred leader the current leader
* All other replicas (other than the leader replica) are followers
* In the event of leader replica failure, one of the follower replicas will be promoted as leader
* Followers don't serve client request
* Their only job is to replicate messages from the leader and stay in sync with the leader
* Each follower constantly pulls new messages from the leader using a single socket channel. That way, the follower receives all messages in the same order as written in the leader
* A message is considered committed when all the ISR have been updated
* **Replication flow** 
  * The client fetches the metadata from a bootstrap broker and caches it during initialization
  * If the client gets `NotLeaderForPartition`, it fetches the latest metadata info from a broker and caches it
  * The client sends the message to the leader
  * The leader writes the message to its local log
  * The follower receives all messages in the same order as written in the leader (Followers constantly pull messages from the leader)
  * The follower writes each received message to its own log 
  * The follower Sends an acknowledgment back to the leader
  * Once the leader receives the acknowledgment from all the replicas in ISR, the message is committed
  * The leader advances the HW 
  * the leader sends an acknowledgment to the client
* If the leader replica detects that a follower has fallen behind significantly or is not available, it removes the follower from the ISR. Thus it is possible to have only one ISR (the leader itself) and the producer writing to it effectively without durability (unless the `min.insync.replicas` is set to a value > 1)
* Only in-sync replicas are eligible to be elected as leader in the event of leader failure unless `unclean.leader.election.enable` is set to true
* Every replica maintains some important offset positions
  * HW (High Water Mark) - Offset of the last committed message
  * LEO (Log End Offset) - Tail of the log
* Conditions for ISR
  * It has sent a heartbeat to Zookeeper in last 6 seconds
  * It has requested message from the leader within last 10 seconds (`replica.lag.time.max.ms`)
  * It has fetched the LEO from the leader in the last 10 seconds
* Leader replica shares its HW to the followers by piggybacking the value with the return value of the fetch requests from the followers
* From time to time, followers checkpoint their HW to its local disk
* When a follower comes back after failure, it truncates all the logs after the last check pointed HW and then reads all the logs from the leader after the given HW
* When a partition leader fails, the new leader chooses its LEO as HW (Follower LEO is usually behind leader HW)
* When a new partition leader is elected, the followers truncate their logs after the last check pointed HW and then read all the logs from the new leader after the given HW
* The new partition leader (before making it available for reads and writes by clients) waits until all the surviving ISR have caught up or a configured period has passed
* **Controller** is a broker that is responsible for electing partition leader (in addition to regular broker responsibilities)
* The first broker that starts in the cluster will become the controller
* If the controller goes down, another broker will become the controller with a higher epoch number and thus preventing "split brain" from occuring
* **Client**
  * Must send the fetch requests to the leader replica of a given partition (otherwise "Not a leader" error is returned)
  * Knows about the replica and broker details for a given topic using metadata requests
  * Caches the metadata information
  * fetches metadata information when metadata.max.age.ms expires or "Not a leader" error is returned (partition leader moved to a different broker due to failure)
  * Metadata requests can be sent to any broker because all brokers cache this information
* Kafka consumer stores the last offset read in a special Kafka topic

### Retention 

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
  * Default log retention time is 7 days (`log.retention.ms`)
  * Retention by time is done based on the last modification time of the segment file (which is usually the last time a message is added to the segment unless some admistrative activities moved the partitions across brokers resulting is excess retention)
  * Default log retention size is 1 GB (`log.retention.bytes`). This configuration is applicable per partition and NOT per topic
  * `log.retention.ms`, `log.retention.minutes` & `log.retention.hours` - If more than one of these parameters are set, the smallest unit takes precedence
  * If both log retention by size and time are configured, messages will be removed when either criteria is met

### Delivery Symantics

* **Idempotence** - `enable.idempotence`
  * Each producer will be assigned a PID by the broker

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
* `buffer.memory` - The memory buffer that will store the messages before being sent out to a broker. If the buffer runs out of space, the thread will remain blocked for `max.block.ms` before throwing an exception. This setting should correspond roughly to the total memory the producer will use, but is not a hard bound since not all memory the producer uses is used for buffering. Some additional memory will be used for compression (if compression is enabled) as well as for maintaining in-flight requests
* `compression.type` - By default messages are uncompressed. Supported compression algorithms - `gzip`, `snappy`, `lz4` and `zstd`
* `retries` - In case of retriable errors, the producer will retry these many times at an interval of `retry.backoff.ms`. Allowing retries without setting max.in.flight.requests.per.connection to 1 will potentially change the ordering of records because if two batches are sent to a single partition, and the first fails and is retried but the second succeeds, then the records in the second batch may appear first. Note additionally that produce requests will be failed before the number of retries has been exhausted if the timeout configured by `delivery.timeout.ms` expires first before successful acknowledgement. Users should generally prefer to leave this config unset and instead use `delivery.timeout.ms` to control retry behavior
* `batch.size` - When multiple records are sent to the same partition, the producer will batch them together. This parameter controls the amount of memory in bytes (not messages!) that will be used for each batch
* `linger.ms` - linger.ms controls the amount of time to wait for additional messages before sending the current batch. KafkaProducer sends a batch of messages either when the current batch is full or when the linger.ms limit is reached
* `client.id` - This can be any string, and will be used by the brokers to identify messages sent from the client. It is used in logging and metrics, and for quotas


## Kafka Consumers

* If all the consumer instances have the same consumer group, then the records will effectively be load balanced over the consumer instances
* If all the consumer instances have different consumer groups, then each record will be broadcast to all the consumer processes
* Each consumer instance in a consumer group is the exclusive consumer of a "fair share" of partitions at any point in time


## Kafka Streams

* StreamsConfig.APPLICATION_ID_CONFIG (application.id) -
  * Mandatory
  * Consumer group id
  * Internal topic name prefix
  * Client id prefix
* ConsumerConfig.AUTO_OFFSET_RESET_CONFIG (auto.offset.reset) - 
  * It's applicable when the consumer group doesn't have any offset associated in Kafka
  * The possible values are - earliest (read the messages from the begining), latest (read the new messages)

## Kafka Connect

* Transparently handles the failure of Kafka brokers
* Transparently adapts as topic partitions it fetches migrate within the cluster
* Consumer is not thread-safe

## Miscellaneous

### Hardware & OS

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

### ZooKeeper Basics

* A Zookeeper cluster is called an ensemble
* Due to the algorithm used, it is recommended that ensembles contain an odd number of servers (e.g., 3, 5, etc.)
* A three-node ensemble can run with one node missing
* A five-node ensemble can run with two nodes missing
* It's a good idea to run Zookeeper in a five-node ensemble so that the ensemble can run even when one node goes down while another node is taken down due to configuration change etc.

### Avro Basics



## References

* Narkhede, Neha. Kafka: The Definitive Guide . O'Reilly Media. Kindle Edition
* https://jira.apache.org/jira/projects/KAFKA/issues
* https://cwiki.apache.org/confluence/collector/pages.action?key=KAFKA
* https://kafka.apache.org/
* Udemy courses by https://www.udemy.com/user/stephane-maarek/
