# Kafka Study

**Kafka Latest Version - 2.4.0**

- [Commands](#commands)

- [Kafka Basics](#kafka-basics)

  - [Introduction](#introduction)

  - [Performance](#performance)

  - [Replication](#replication)

  - [Retention](#retention)

  - [Rebalancing](#rebalancing)

  - [Delivery Symantics](#delivery-symantics)

  - [Quota](#quota)

  - [Broker Config](#broker-config)

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
  * List of consumer groups owned by a Co-ordinator and their membership information
* **Internal topics**
  * __transaction_state
  * __consumer_offsets
* CRC32 is used to check if the messages are corrupted
* **Shutdown** - When a server is stopped gracefully it has two optimizations it will take advantage of:
  * It will sync all its logs to disk to avoid needing to do any log recovery when it restarts (i.e. validating the checksum for all messages in the tail of the log). Log recovery takes time so this speeds up intentional restarts.
  * It will migrate any partitions the server is the leader for to other replicas prior to shutting down. This will make the leadership transfer faster and minimize the time each partition is unavailable to a few milliseconds.
* Syncing the logs will happen automatically whenever the server is stopped other than by a hard kill, but the controlled leadership migration requires using a   special setting:
  ```
  controlled.shutdown.enable=true
  ```
* Note that controlled shutdown will only succeed if all the partitions hosted on the broker have replicas (i.e. the replication factor is greater than 1 and     at least one of these replicas is alive)

### Performance

* Modern operating systems are designed to aggressively use the available memory as page cache. Thus if the server hosting Kafka broker is not used for other applications, more page cache will be available for Kafka
* When consumers are lagging only a little from the producer, the broker doesn't need to reread the messages from the disk. The requests can be served directly from the page cache
* Using pagecache has several advantages over an in-process cache for storing data that will be written out to disk:
  * The I/O scheduler will batch together consecutive small writes into bigger physical writes which improves throughput.
  * The I/O scheduler will attempt to re-sequence writes to minimize movement of the disk head which improves throughput.
  * It automatically uses all the free memory on the machine
* Compact byte structure rather than Java objects reduces Java Object ovehead
* Not having an in-process cache for messages makes more memory available for page cache & reduces garbage collection issues with increased in-heap data
* Simple reads and appends to file resulting in sequential disk access
* Transfer batches of messages over the network to amortize the network roundtrip, do larger sequential disk access, allocate contiguous memory blocks, provide good compression ratio
* Zero-copy - `sendfile` system call of Linux reduces byte copying (across kernel and user spaces) and context switches
* Standardized binary format shared by producer, broker & consumer reduces recopying & transformation
* Compression of message batch saves network bandwidth
* No intervening routing tier. Messages of a given partition are sent directly to the partition leader
* Consumers use "long poll" to avoid busy waiting and ensure larger transfer sizes

Note: Application level flushing (fsync) gives less leeway to the OS to optimize the writes. The Linux fsync implementation blocks all writes to the file, whereas the OS level flushing makes more granular level locks

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
* If all the replicas crash, the data that is committed but not written to the disk are lost

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

### Rebalancing

* Rebalancing is the process where a group of consumer instances (belonging to the same group) co-ordinate to own a mutually exclusive set of partitions of       topics that the group has subscribed to
* Every partition for all subscribed topics will be owned by a single consumer instance
* One of the brokers is elected as the coordinator for a subset of the consumer groups
* Co-ordinator role
  * Triggering rebalancing whenever required (new members connect with co-ordinator, failed consumers are identified by failure detection run by the co-ordinator)
  * Communicating the resulting partition-consumer ownership configuration to all consumers of the group undergoing a rebalance operation
* Essentially, a rebalance kicks in when following conditions are met:
  * Group membership changes, such as a new member joining
  * Member subscription changes, such as one consumer changing the subscribed topics
  * Resource changes, such as adding more partitions to the subscribed topic
* Stages
  * Rebalance - 
    * Kill the socket connection to any consumers in the group
    * The consumers re-register with the co-ordinator
  * Sync
    * The group coordinator replies to all members with the latest generation information to fence off any previous generation consumers
    * Nominates one of the members as the leader and replies to the leader with encoded membership and subscription metadata
    * The leader shall complete the assignment based on membership and topic metadata information, and reply to the coordinator with the assignment information
    * All the followers are required to send a sync group request to get their actual assignments
    * Co-ordinator responds back with the assignment information
  * Stable
* Static membership with `group.instance.id` so that the same set of partitions are assigned back to the member after restart without a fresh rebalancing


### Delivery Symantics

* Kafka supports
  * At most once — Messages may be lost but are never redelivered.
  * At least once — Messages are never lost but may be redelivered.
  * Exactly once — this is what people actually want, each message is delivered once and only once.
* **Idempotence** - `enable.idempotence`
  * Each producer will be assigned a PID by the broker

### Quota

* Broker config
  ```
  quota.window.num
  quota.window.size.seconds
  replication.quota.window.num
  replication.quota.window.size.seconds
  ```
* `quota.window.num` or `replication.quota.window.num` specifies the number of samples to be retained in memory for the calculation
* `Throttle Time = (overall produced in window - quotabound)/Quota limit per second`
* If the client exceeds the quota, the broker responds with the throttle time X asking the client to refrain from sending further requests for time X
* Two types of client quotas can be enforced by Kafka brokers for each group of clients sharing a quota:
  * Network bandwidth quotas define byte-rate thresholds (since 0.9)
  * Request rate quotas define CPU utilization thresholds as a percentage of network and I/O threads (since 0.11)
* Quotas can be applied to (user, client-id), user or client-id groups
* For a given connection, the most specific quota matching the connection is applied
* Quota overrides are written to ZooKeeper
* By default, each unique client group receives a fixed quota as configured by the cluster. This quota is defined on a per-broker basis

### Broker Config

* `auto.create.topics.enable` - Default value is true. It should be set to false in production as there is no way to validate the topic names.
* **Message Size**
  * `message.max.bytes` defaults to 1MB
  * `message.max.bytes` indicates the compressed message size
  * `fetch.message.max.bytes` (consumer) & `replica.fetch.max.bytes` must match with `message.max.bytes`
  * Larger `message.max.bytes` will impact disk I/O throughput

## Kafka Producers

* `KafkaProducer<K,V>` is thread safe and sharing a single producer instance across threads will generally be faster than having multiple instances
* The `send()` method is asynchronous. When called it adds the record to a buffer of pending record sends and immediately returns
* The producer maintains buffers of unsent records for each partition. These buffers are of a size specified by the `batch.size config`
* If the buffer is full or metadat is not available, the `send()` method blocks for `max.block.ms` and throws a `TimeoutException` after that
* **Message Reliability (Typical Scenario)**
  * **Producer Config**
    ```
    # Producers won't get acknowledgement until all the in-sync replicas are updated
    acks = all

    # Default value. With idempotence enabled, there is no risk of duplicates
    retries = Integer.MAX_VALUE 

    # To get exacly once delivery symantic
    enable.idempotence = true

    # Default value. With idempotence enabled, there won't be any duplicate and it cannot be more than 5
    max.in.flight.requests.per.connection = 5

    # Default value. Should be >= request.timeout.ms and linger.ms. An upper bound on the time to report success or failure after a call to send()
    # Retries will stop after this duration
    delivery.timeout.ms = 120000
    ```
    **Broker / Topic Config**
    ```
    default.replication.factor = 3

    # Consumers won't get message until the message is committed i.e. all in-sync replicas are updated
    # If 2 ISR are not available, the producer will throw an exception (either NotEnoughReplicas or NotEnoughReplicasAfterAppend)
    min.insync.replicas = 2
    ```
* **Mandatory Parameters**
  * `key.serializer`
  * `value.serializer`
  * `bootstrap.servers` - List host:port of brokers. All brokers need not be present and the producer will discover about other brokers from metadata. However, more than one broker should be specified so that the producer can connect even in the event of a broker failure
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
* `buffer.memory` - The memory buffer that will store the messages before being sent out to a broker. If the buffer runs out of space, the thread will remain blocked for `max.block.ms` before throwing an exception. This setting should correspond roughly to the total memory the producer will use, but is not a hard bound since not all memory the producer uses is used for buffering. Some additional memory will be used for compression (if compression is enabled) as well as for maintaining in-flight requests
* `compression.type` - By default messages are uncompressed. Supported compression algorithms - `gzip`, `snappy`, `lz4` and `zstd`
* `batch.size` - When multiple records are sent to the same partition, the producer will batch them together. This parameter controls the amount of memory in bytes (not messages!) that will be used for each batch
* `linger.ms` - linger.ms controls the amount of time to wait for additional messages before sending the current batch. KafkaProducer sends a batch of messages either when the current batch is full or when the linger.ms limit is reached
* `client.id` - This can be any string, and will be used by the brokers to identify messages sent from the client. It is used in logging and metrics, and for quotas

## Kafka Consumers

* Transparently handles the failure of Kafka brokers
* Transparently adapts as topic partitions it fetches migrate within the cluster
* Consumer is NOT thread-safe
* If all the consumer instances have the same consumer group, then the records will effectively be load balanced over the consumer instances
* If all the consumer instances have different consumer groups, then each record will be broadcast to all the consumer processes
* Each consumer instance in a consumer group is the exclusive consumer of a "fair share" of partitions at any point in time
* `session.timeout.ms` defines how long the coordinator waits after the member’s last heartbeat before it assuming the member failed
  * With a low value, a network jitter or a long garbage collection (GC) might fail the liveness check, causing the group coordinator to remove this member and begin rebalancing
  * With a longer value, there will be a longer partial unavailability when a consumer actually fails
* A consumer can look up its coordinator by issuing a `FindCoordinatorRequest` to any Kafka broker and reading the `FindCoordinatorResponse` which will contain the coordinator details
* The consumer can then proceed to commit or fetch offsets from the coordinator broker
* The broker sends a successful offset commit response to the consumer only after all the replicas of the offsets topic receive the offsets
* In case the offsets fail to replicate within a configurable timeout, the offset commit will fail and the consumer may retry the commit after backing off
* The brokers periodically compact the offsets topic since it only needs to maintain the most recent offset commit per partition
* The coordinator also caches the offsets in an in-memory table in order to serve offset fetches quickly

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

* Kafka Connect is a tool for scalably and reliably streaming data between Apache Kafka and other systems
* Connectors can be configured with transformations to make lightweight message-at-a-time modifications
* To copy data between Kafka and another system, users create a Connector for the system they want to pull data from or push data to
* Connectors come in two flavors: `SourceConnectors` import data from another system (e.g. `JDBCSourceConnector` would import a relational database into Kafka)     and `SinkConnectors` export data (e.g. `HDFSSinkConnector` would export the contents of a Kafka topic to an HDFS file)
* Connectors do not perform any data copying themselves: their configuration describes the data to be copied, and the Connector is responsible for breaking       that job into a set of Tasks that can be distributed to workers
* Tasks also come in two corresponding flavors: `SourceTask` and `SinkTask`
* With an assignment in hand, each Task must copy its subset of the data to or from Kafka
* The SourceTask implementation included a stream ID (the input filename) and offset (position in the file) with each record. The framework uses this to commit   offsets periodically so that in the case of a failure, the task can recover and minimize the number of events that are reprocessed and possibly duplicated


## Miscellaneous

### Hardware OS

* Java 8 with G1 Collector
* On AWS, for lower latency I/O optimized instances will be good
* Extents File System (XFS) perform well for Kafka Workload
* the mountpoints should have `noatime` option set to eliminate the 
* export KAFKA_JVM_PERFORMANCE_OPTS="-server -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:InitiatingHeapOccupancyPercent=35 -XX:+DisableExplicitGC-Djava.awt.headless=true"
* `vm.swappiness = 1` - A low value means the kernel will try to avoid swapping as much as possible making less memory available for page cache
* `vm.dirty_background_ratio = 5` (default - 10) - The percentage of system memory which when dirty, system will start writing to disk
* `vm.dirty_ratio = 60` (default - 15) - The percentage of memory which when dirty, the process doing writes would block and write out dirty pages to thedisks
* Tuning `vm.dirty_background_ratio` and `vm.dirty_ratio` as above relies on:
  * Good disk I/O performance (SSD or RAID)
  * Replication is used in the cluster to guard against system failure
* `net.core.wmem_default = 131072` (128 KiB)
* `net.core.rmem_default = 131072` (128 KiB)
* `net.core.wmem_max = 131072` (128 KiB)
* `net.core.rmem_max = 2097152` (2 MiB)
* `net.ipv4.tcp_wmem = 4096 65536 2048000` (4 KiB 64 KiB 2 MiB)
* `net.ipv4.tcp_rmem = 4096 65536 2048000` (4 KiB 64 KiB 2 MiB)
* `vm.max_map_count` = 65535 - Maximum number of memory map areas a process may have (aka `vm.max_map_count`). By default, on a number of Linux systems, the value of `vm.max_map_count` is somewhere around 65535. Each log segment, allocated per partition, requires a pair of index/timeindex files, and each of these files consumes 1 map area. In other words, each log segment uses 2 map areas. Thus, each partition requires minimum 2 map areas, as long as it hosts a single log segment. That is to say, creating 50000 partitions on a broker will result allocation of 100000 map areas and likely cause broker crash with OutOfMemoryError (Map failed) on a system with default `vm.max_map_count`
* File descriptor limits: Kafka uses file descriptors for log segments and open connections. If a broker hosts many partitions, consider that the broker needs at least (number_of_partitions)*(partition_size/segment_size) to track all log segments in addition to the number of connections the broker makes. We recommend at least 100000 allowed file descriptors for the broker processes as a starting point

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
* https://www.confluent.io/blog/
