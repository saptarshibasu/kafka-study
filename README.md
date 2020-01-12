# Kafka Study

**Kafka Latest Version - 2.4.0**

- [Kafka Basics](#kafka-basics)

  - [What Why of Kafka](#what-why-of-kafka)

  - [Basics](#basics)

  - [Performance](#performance)

  - [Replication](#replication)

  - [Retention](#retention)

  - [Compaction](#compaction)

  - [Rebalancing Consumer Groups](#rebalancing-consumer-groups)

  - [Delivery Symantics](#delivery-symantics)

  - [Quota](#quota)

  - [Broker Config](#broker-config)

- [Kafka Producers](#kafka-producers)

- [Kafka Consumers](#kafka-consumers)

- [Kafka Streams](#kafka-streams)

- [Kafka Connect](#kafka-connect)

- [Schema Registry](#schema-registry)

- [KSQL](#ksql)

- [Kafka CLI](#kafka-cli)

- [Miscellaneous](#kafka-miscellaneous)

  - [Production Deployment](#production-deployment)

  - [ZooKeeper Basics](#zooKeeper-basics)

  - [Avro Basics](#avro-basics)

- [References](#references)


## Kafka Basics

### What Why of Kafka

* Kafka originally designed as a message queue has developed into a distributed streaming platform. It is based on a **distributed, partitioned, replicated**     log
* As a messaging system, what makes Kafka different from the traditional messaging systems is its log centric design which makes it suitable for use cases that    cannot be so easily and elegantly addressed with other messaging systems
  * **Developing applications optimized for both read and write** - Kafka allows capturing application data as an ordered series of events which can then be processed by different consumer processes to produce various read optimized views. Thus, while writes are efficient appends, reads are from read-optimized pre-aggregated denormalized views updated in near-real time. This pattern of developing applications where the read and write models are decoupled is also known as **Command Query Responsibility Seggregation (CQRS)** and we implement it using another pattern called **Event Sourcing** which requires the data to be captured as an ordered series of immutable events (as opposed to a state machine) to ensure that the historical information is preserved
  * **Replacing dual-writes with log based writes** - Applications writing to multiple systems (viz. search index, database, cache, hadoop etc.) can suffer from race condition and partial failure leading to perpetually inconsistent data. Applications can instead write to Kafka and then have the individual systems consume the ordered event stream from Kafka to create their internal state in a deteministic way
  * **Change Data Capture** - Applications writing directly to database can source an event stream from the DB **Write Ahead Log (WAL)** to Kafka via Kafka Connect. The indiviadual applications can then prepare their respective views from the Kafka event stream
  * **Materialized View Pattern** - In a microservice based architecture, individual microservices can consume events from other services and create it's own pre-aggregated **Materialized View** and serve data from there instead of querying the respective services during run time. This will lead to reduced latency and loose-coupling between services
  * **Recreating views** - Sourcing the event stream from Kafka the views can be recreated at anytime after a crash (viz. pre-warming a cache after a restart). This aspect can also be exploited to debug applications by replaing the events to recreate the exact scenario
* Some key aspects of Kafka - 
  * Kafka is a modern day fault-tolerant distributed system which scales horizontally storing more data that a single machine can hold
  * Kafka provides ordering guarantees within a partition
  * Kafka's durability using distributed replicated partitions allows it to serve as a single source of truth
  * Kafka's compaction cleaning policy allows it to store compacted data for an indefinite period of time
  * Kafka Connect API makes it easy to import/export data from or to other systems
  * Kafka Streamimg API allows processing event stream with advanced joining & windowing primitives
  * Kafka's consumer groups allow both queue (load balancing across consumer processes) and publish-subscribe (multiple consumer groups) at the same time
  * Kafka's zero copy, page cache leverage, message batching, smaller heap and sequential disk access provide good performance
  * Kafka's performance is effectively constant with respect to data size so storing data for a long time is not a problem
  * Kafka's consumers keep track of their progress in separate internal Kafka topics adding very little overhead to the brokers
  * Kafka supports space efficient publish-subscribe as there is only single log no matter how many consumer groups are there

### Basics

* Data is organized among **topics**
* Multiple producers can write to the same topic or multiple topics
* Each topic is **partitioned**
* Partitions are evenly distributed across the available brokers to distribute the load evenly
* Typically, there will be many more partitions than servers or brokers
* Partitions serve two purposes -
  * Allow storing more records a single machine can hold
  * Serve as a unit of parallelism
* Producers and Consumers are Kafka clients
* A message in kafka is a key-value pair with a small amount of associated metadata
* A message set is a group of messages and is the unit of compression
* The broker always decompresses the batch to validate
* Kafka only provides a total order over records within a partition, not between different partitions in a topic
* As Kafka writes its data to local disk, the data may linger in the filesystem cache and may not make its way to the disk. The disk flush parameter are not      recommended to set for performance reasons. Therefore Kafka relies solely on replication for reliability
* In Linux, data written to the filesystem is maintained in pagecache until it must be written out to disk (due to an application-level fsync or the OS's         ownflush policy)
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
  * It will sync all its logs to disk to avoid needing to do any log recovery when it restarts (i.e. validating the checksum for all messages in the tail of the log). Log recovery takes time so this speeds up intentional restarts
  * It will migrate any partitions the server is the leader for to other replicas prior to shutting down. This will make the leadership transfer faster and minimize the time each partition is unavailable to a few milliseconds.
* Syncing the logs will happen automatically whenever the server is stopped other than by a hard kill, but the controlled leadership migration requires using a   special setting: `controlled.shutdown.enable=true`
* Note that controlled shutdown will only succeed if all the partitions hosted on the broker have replicas (i.e. the replication factor is greater than 1 and     at least one of these replicas is alive)
* Default ports
  * Zookeeper: 2181
  * Zookeeper Leader Port 3888
  * Zookeeper Election Port (Peer port) 2888
  * Broker: 9092
  * REST Proxy: 8082
  * Schema Registry: 8081
  * KSQL: 8088
* Prior to release 2.4.0, the records with null keys will be distrubuted across partitions in a round-robin manner
* After release 2.4.0, with the introduction of sticky partitioning strategy, the records will be routed to the same partition until the batch completion         criteria (`batch.size` & `linger.ms`) are fulfilled
* **Rack Awareness** -
  * `broker.rack` - If set for a broker, the replicas will be placed to ensure all replicas are not on the same rack. Useful when the brokers are spread across    datacenters or AZ in AWS within the same region
  * `client.rack` - Release 2,4,0 onwards, it allows the consumer to fetch from a follower replica on the same rack, if the partion leader is not available on the same rack

### Performance

* Modern operating systems are designed to aggressively use the available memory as page cache. Thus if the server hosting Kafka broker is not used for other     applications, more page cache will be available for Kafka
* When consumers are lagging only a little from the producer, the broker doesn't need to reread the messages from the disk. The requests can be served directly   from the page cache
* Using pagecache has several advantages over an in-process cache for storing data that will be written out to disk:
  * The I/O scheduler will batch together consecutive small writes into bigger physical writes which improves throughput
  * The I/O scheduler will attempt to re-sequence writes to minimize movement of the disk head which improves throughput
  * It automatically uses all the free memory on the machine
* Compact byte structure rather than Java objects reduces Java Object ovehead
* Not having an in-process cache for messages makes more memory available for page cache & reduces garbage collection issues with increased in-heap data
* Simple reads and appends to file resulting in sequential disk access
* Transfer batches of messages over the network to amortize the network roundtrip, do larger sequential disk access, allocate contiguous memory blocks, provide   good compression ratio
* Zero-copy - `sendfile` system call of Linux reduces byte copying (across kernel and user spaces) and context switches
* Standardized binary format shared by producer, broker & consumer reduces recopying & transformation
* Compression of message batch saves network bandwidth
* No intervening routing tier. Messages of a given partition are sent directly to the partition leader
* Consumers use "long poll" to avoid busy waiting and ensure larger transfer sizes

Note: Application level flushing (fsync) gives less leeway to the OS to optimize the writes. The Linux fsync implementation blocks all writes to the file, whereas the OS level flushing makes more granular level locks

### Replication

* Each partition can have multiple replicas. For most production system 3 replicas are good enough
* Each broker hosts hundreds or thousands of replicas belonging to different topics
* Replication factor must be less than or equal to the number of brokers up and running
* Kafka recommends using replication for durability and not set the disk flush parameters
* Each partition has a leader replica
* All producer & consumer requests go through the leader replica to guarantee consistency
* Leaders keep track of the last offsets fetched by each replica
* For each partition, Kafka stores in Zookeeper the current leader and the current In-sync replicas (ISR)
* Preferred leader - The replica that was the leader when the topic was originally created
* When the preferred leader is indeed the leader, the load is expected to be more evenly distributed among the brokers
* By default, Kafka is configured with `auto.leader.rebalance.enable=true`, which will check if the preferred leader replica the current leader. If not and if    it is ISR, a leader election will be triggered to make the preferred leader the current leader
* All other replicas (other than the leader replica) are followers
* In the event of leader replica failure, one of the follower replicas will be promoted as leader
* If the leader replica detects that a follower has fallen behind significantly or is not available, it removes the follower from the ISR. Thus it is possible    to have only one ISR (the leader itself) and the producer writing to it effectively without durability (unless the `min.insync.replicas` is set to a value >    1)
* Only in-sync replicas are eligible to be elected as leader in the event of leader failure unless `unclean.leader.election.enable` is set to true
* Every replica maintains some important offset positions
  * HW (High Water Mark) - Offset of the last committed message
  * LEO (Log End Offset) - Tail of the log
* Conditions for ISR
  * It has sent a heartbeat to Zookeeper in last 6 seconds
  * It has requested message from the leader within last 10 seconds (`replica.lag.time.max.ms`)
  * It has fetched the LEO from the leader in the last 10 seconds
* Followers don't serve client request. Their only job is to replicate messages from the leader and stay in sync with the leader
* Each follower constantly pulls new messages from the leader using a single socket channel. That way, the follower receives all messages in the same order as    written in the leader
* A message is considered committed when all the ISR have been updated
* Consumers don't see uncommitted messages regardless of the `acks` setting which affects only the acknowledgement to the producer
* **Replication flow** 
  * The client fetches the metadata from a bootstrap broker and caches it during initialization
  * If the client gets `NotLeaderForPartition`, it fetches the latest metadata info from a broker and caches it
  * The client sends the message to the leader
  * The leader writes the message to its local log
  * The follower receives all messages in the same order as written in the leader (Followers constantly pull messages from the leader)
  * The follower writes each received message to its own log 
  * The follower Sends an acknowledgment back to the leader
  * Once the leader receives the acknowledgment from all the replicas in ISR or a timeout occurs, the message is committed
  * The leader advances the HW 
  * the leader sends an acknowledgment to the client
* Leader replica shares its HW to the followers by piggybacking the value with the return value of the fetch requests from the followers
* From time to time, followers checkpoint their HW to its local disk (`replica.high.watermark.checkpoint.interval.ms`)
* When a follower comes back after failure, it truncates all the logs after the last check pointed HW and then reads all the logs from the leader after the       given HW
* When a partition leader fails, the new leader chooses its LEO as HW (Follower LEO is usually behind leader HW)
* When a new partition leader is elected, the followers truncate their logs after the last check pointed HW and then read all the logs from the new leader        after the given HW
* The new partition leader (before making it available for reads and writes by clients) waits until all the surviving ISR have caught up or a configured period   has passed
* **Controller** is a broker that is responsible for electing partition leader (in addition to regular broker responsibilities)
  * The first broker that starts in the cluster will become the controller
  * If the controller goes down, another broker will become the controller with a higher epoch number and thus preventing "split brain" from occuring
* **Client**
  * Must send the fetch requests to the leader replica of a given partition (otherwise "Not a leader" error is returned)
  * Knows about the replica and broker details for a given topic using metadata requests
  * Caches the metadata information
  * fetches metadata information when `metadata.max.age.ms` expires or "Not a leader" error is returned (partition leader moved to a different broker due to failure)
  * Metadata requests can be sent to any broker because all brokers cache this information
* If all the replicas crash, the data that is committed but not written to the disk are lost
* Kafka MirrorMaker provides geo-replication support for your clusters. With MirrorMaker, messages are replicated across multiple datacenters or cloud regions.   You can use this in active/passive scenarios for backup and recovery; or in active/active scenarios to place data closer to your users, or support data         locality requirements
* To support fetching from the follower replica (because the partition leader is not available on the same rack) based on HW, Kafka leader doesn't delay          responding to replica fetch requests if the follower has obsolete HW

### Retention 

* **Segments**
  * Each partition is split into multiple segments
  * By default, each segment contains 1 GB of data (`log.segment.bytes`) or 1 week worth of data (`log.roll.ms` or `log.roll.hours`) whichever is smaller
  * If either of the segment limits is reached, the segment file is closed and a new segment file is created
  * Kafka brokers always keeps 3 open file handles for all the segments of all the partition - even inactive segments
    * File handle to the segment file
    * File handle to the offset index of the segment file
    * File handle to the time index of the segment file (introduced in release 0.10.1.0)
  * Offset index contains a mapping between a relative message offset (within the segment) and the corresponding physical location in the segment file. This      allows Kafka to quickly lookup a given offset in a segment file
  * Time index contains a mapping between a relative message offset (within the segment) and the corresponding message time (LogAppendTime or CreateTime          depending on `log.message.timestamp.type`). The following functionalities will refer to the time index
    * Search based on timestamp
    * Time based retention
    * Time based rotation
  * The segment being currently written to for a given partition is called active segment
  * Active segments are never deleted even if the retention criteria is met
  * If the retention policy is set as "delete", the old segments are deleted depending on retention criteria
  * Smaller log segments mean frequent closing and allocation of files reducing overall disk performance
* **Deciding Number of Partitions**
  * Avoid underestimating as one partition will always be read by only one consumer
  * Avoid overestimating as each partition uses memory and other resources on a broker and increases the leader election time
* **Log Retention**
  * ~~Retention by time is done based on the last modification time of the segment file (which is usually the last time a message is added to the segment unless some admistrative activities moved the partitions across brokers resulting is excess retention)~~ (Prior to release 0.10.1.0)
  * Default log retention size is 1 GB (`log.retention.bytes`). This configuration is applicable per partition and NOT per topic
  * `log.retention.ms`, `log.retention.minutes` & `log.retention.hours` - If more than one of these parameters are set, the smallest unit takes precedence
  * `log.retention.check.interval.ms` = The frequency at which the log cleaner checks if there is any log for deletion 

### Compaction

* `log.cleanup.policy = compact`
* Kafka will always retain at least the last known value for each message key within the log of data for a single topic partition
* It serves use cases to restore a system or pre-warm a cache after a crash. Essentially it serves the use cases that needs the latest values for ther complete   set of keys rather than the most recent changes
* Compaction is useful to implement event sourcing or materialized view pattern
* The original offset of the messages are not changed
* Compaction also allows for deletes. A message with a key and a null payload will be treated as a delete from the log
* Delete markers will themselves be cleaned out of the log after a period of time to free up space
* It is possible for a consumer to miss delete markers if it lags by more than `delete.retention.ms` (default 24 hrs)
* The compaction is done in the background by periodically recopying log segments
* Cleaning does not block reads and can be throttled to use no more than a configurable amount of I/O throughput to avoid impacting producers and consumers
* The topic's `min.compaction.lag.ms` can be used to guarantee the minimum length of time must pass after a message is written before it could be compacted
* Compaction will never re-order messages, just remove some
* Cleaning is done by a separate pool of threads `log.cleaner.threads` at an interval 

### Rebalancing Consumer Groups

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
* Static membership with `group.instance.id` so that the same set of partitions are assigned back to the member after temporary network partitioning (within `session.timeout`) without a fresh rebalancing
* Release 2.4.0 onwards, incremental rebalance protocol tries to minimize the partition migration and let the consumers retain their partitions during            rebalancing


### Delivery Symantics

* Exactly once semantic was introduced in version 0.11 with support from the following features
  * idempotence - `enable.idempotence` (broker config)
  * transaction - `transactional.id` (producer config)
* Kafka supports
  * At most once — Messages may be lost but are never redelivered.
  * At least once — Messages are never lost but may be redelivered.
  * Exactly once — this is what people actually want, each message is delivered once and only once.
* Producer Perspective
  * At most once - No retry if successful response from broker is not received (`retries = 0`)
  * At least once - If no success response received from broker, retry again (`retries = Integer.MAX_VALUE`, `delivery.timeout.ms = 120000`)
  * Exactly once - 
    * `retries = Integer.MAX_VALUE`, `delivery.timeout.ms = 120000`, `enable.idempotence = true`
    * Broker assigns each new producer a unique id (PID) and keeps a sequence number per partition that starts from 0 and increments with each message sent
    * Relying on the in-order property of Kafka (and TCP), the broker will only keep track of the highest sequence number seen and reject any sequence number which is not exacly 1 higher than the last seen number
    * Messages with a lower sequence number result in a duplicate error, which can be ignored by the producer
    * Messages with a higher number result in an out-of-sequence error, which indicates that some messages have been lost, and is fatal
    * The PID, producer epoch and the first sequence number within the set will be stored at the message set level to reduce the overhead at message level
    * Since each new instance of a producer is assigned a new unique PID, we can only guarantee idempotent production within a single producer session
    * Producers lease PIDs for a fixed duration of time from the co-ordinator broker to ensure that the brokers don't go out-of-memory to keep track of all PIDs
* Consumer Perspective
  * At most once - `enable.auto.commit` commits the offset even before the messages were processed
  * At least once - The writes to the down stream system when retried without the offsets
  * Exactly once - The offsets can be saved in the downstream system along with the messages. In a key value DB, the message will be simply overwritten and in a relational DB, both can be saved in a transaction
* Transactions across partitions are useful for streaming - consume-process-produce - as consume is also about producing offset messages to keep track of progress, consuming and producing in streaming need to be done in a transaction
* `transactional.id` is a user provided unique id per producer. It serves two purposes
  * Allows only one transaction to proceed for a given producer
  * Allows recovering a transaction after restart
* Transaction expiry is configured using - `transactional.id.expiration.ms`
* Kafka default `isolation.level` is `read_uncommitted`
* `read_uncommitted` - consume both committed and uncommitted messages in offset ordering.
* `read_committed` - Only consume non-transactional messages or committed transactional messages in offset order. The end offset of a partition for a             read_committed consumer would be the offset of the first message in the partition belonging to an open transaction. This offset is known as the 'Last Stable    Offset'(LSO)
* Kafka Streams sets the internal embedded producer client with a transaction id to enable the idempotence and transactional messaging features, and also sets    its consumer client with the read-committed mode to only fetch messages from committed transactions from the upstream producers

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

* `auto.create.topics.enable` - Default value is true. It should be set to false in production as there is no way to validate the topic names
* Tuning for Throughput
  * `compression.type = producer` (default value) - The broker will uncompress the batch to validate and then send the compressed message from producer           directly to the consumer
* Tuning for latency
  * `num.replica.fetchers` - Number of fetcher threads per broker. The threads are used to replicate the messages. Many partitions increase the latency as by      default there is only one thread per broker to do fetching from the leader broker for replication
* Tuning for durability -
  * `default.replication.factor = 3`
* **Message Size**
  * `message.max.bytes` defaults to 1MB
  * `message.max.bytes` indicates the compressed message size
  * `fetch.message.max.bytes` (consumer) & `replica.fetch.max.bytes` must match with `message.max.bytes`
  * Larger `message.max.bytes` will impact disk I/O throughput
  * `compression.type` - Accepts the standard compression codecs ('gzip', 'snappy', 'lz4', 'zstd'). It additionally accepts 'uncompressed' which is equivalent    to no compression; and 'producer' which means retain the original compression codec set by the producer
  * `lz4` is the fastest among the compression algorithms (around 800 mbps)

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
* `buffer.memory` - The memory buffer that will store the messages before being sent out to a broker. If the buffer runs out of space, the thread will remain     blocked for `max.block.ms` before throwing an exception. This setting should correspond roughly to the total memory the producer will use, but is not a hard    bound since not all memory the producer uses is used for buffering. Some additional memory will be used for compression (if compression is enabled) as well     as for maintaining in-flight requests
* `compression.type` - By default messages are uncompressed. Supported compression algorithms - `gzip`, `snappy`, `lz4` and `zstd`
* `client.id` - This can be any string, and will be used by the brokers to identify messages sent from the client. It is used in logging and metrics, and for quotas
* In 2.4.0 release, the DefaultPartitioner now uses a sticky partitioning strategy. This means that records for specific topic with null keys and no assigned     partition will be sent to the same partition until the batch is ready to be sent. When a new batch is created, a new partition is chosen. This decreases        latency to produce, but it may result in uneven distribution of records across partitions in edge cases
* Tuning for throughput
  * `acks = 1` (default value) - The leader broker will send the acknowledge as soon as it has writtent the record in int local log. The acknowledgement will     not wait for the replicas to write the record in their logs. The tradeoff is less durability.
  * `compression.type = lz4`
  * `batch.size` - The records for a given partition are usually sent in batches to amortize the network roundtrip, improve compression ratio and reduce the      load of processing messages in producer and broker. The batch will not be sent to the broker until the `batch.size` is full or `linger.ms` is expired. The    tradeoff is higher latency
  * `linger.ms` - The producer will wait for `linger.ms` duration before sending the batch to the broker unless the `batch.size` is full
* Tuning for latency
  * `linger.ms = 0` (default) - The producer will send the message as soon as it has some data to send. Batching will still happen if more messages are           available at the    time of sending

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
* A consumer can look up its coordinator by issuing a `FindCoordinatorRequest` to any Kafka broker and reading the `FindCoordinatorResponse` which will contain   the coordinator details
* The consumer can then proceed to commit or fetch offsets from the coordinator broker
* The broker sends a successful offset commit response to the consumer only after all the replicas of the offsets topic receive the offsets
* In case the offsets fail to replicate within a configurable timeout, the offset commit will fail and the consumer may retry the commit after backing off
* The brokers periodically compact the offsets topic since it only needs to maintain the most recent offset commit per partition
* The coordinator also caches the offsets in an in-memory table in order to serve offset fetches quickly
* **Mandatory Parameters** - 
  * `key.deserializer`
  * `value.deserializer`
  * `bootstrap.servers`
  * `group.id` - A unique string that identifies the consumer group this consumer belongs to. This property is required if the consumer uses either the group     management functionality by using subscribe(topic) or the Kafka-based offset management strategy
  * `heartbeat.interval.ms` - The expected time between heartbeats to the consumer coordinator when using Kafka's group management facilities. Heartbeats are     used to ensure that the consumer's session stays active and to facilitate rebalancing when new consumers join or leave the group. The value must be set       lower than session.timeout.ms, but typically should be set no higher than 1/3 of that value. It can be adjusted even lower to control the expected time for   normal rebalances
  * `max.partition.fetch.bytes` - The maximum amount of data per-partition the server will return. Records are fetched in batches by the consumer. If the first   record batch in the first non-empty partition of the fetch is larger than this limit, the batch will still be returned to ensure that the consumer can make   progress. The maximum record batch size accepted by the broker is defined via `message.max.bytes` (broker config) or `max.message.bytes` (topic config)
  * `session.timeout.ms` - The timeout used to detect client failures when using Kafka's group management facility. The client sends periodic heartbeats to       indicate its liveness to the broker. If no heartbeats are received by the broker before the expiration of this session timeout, then the broker will remove   this client from the group and initiate a rebalance. Note that the value must be in the allowable range as configured in the broker configuration by          `group.min.session.timeout.ms` and `group.max.session.timeout.ms`
  * `allow.auto.create.topics` - Should be set to false in production as there is no way to validate topic name
  * `auto.offset.reset` - What to do when there is no initial offset in Kafka or if the current offset does not exist any more on the server (e.g. because that   data has been deleted). Default value is `latest`
  * `max.poll.interval.ms` - The maximum delay between invocations of `poll()` when using consumer group management. This places an upper bound on the amount     of time that the consumer can be idle before fetching more records. If `poll()` is not called before expiration of this timeout, then the consumer is         considered failed and the group will rebalance in order to reassign the partitions to another member. For consumers using a non-null `group.instance.id`      which reach this timeout, partitions will not be immediately reassigned. Instead, the consumer will stop sending heartbeats and partitions will be            reassigned after expiration of session.timeout.ms
  * `enable.auto.commit` - If true the consumer's offset will be periodically committed in the background. Default value is true and must be set to false in      production 
* **Handling polls for unpredictable message processing time**
  * Move message processing to another thread
  * Allow the consumer to continue calling poll while the processor is still working
  * Some care must be taken to ensure that committed offsets do not get ahead of the actual position
  * Typically, you must disable automatic commits and manually commit processed offsets for records only after the thread has finished handling them 
  * Note also that you will need to pause the partition so that no new records are received from poll until after thread has finished handling those            previously returned
* Setting `enable.auto.commit` means that offsets are committed automatically with a frequency controlled by the config `auto.commit.interval.ms` giving us     "at most once" i.e. commiting before the message processing is complete
* Users can also control when records should be considered as consumed and hence commit their offsets. We will manually commit the offsets only after the       corresponding records have been inserted into the database. This gives us "at least once" delivery semantic where the message will be delivered once, but     in failure cases, it could be possibly delivered twice
* The consumer application need not use Kafka's built-in offset storage, it can store offsets in a store of its own choosing. The primary use case for this     is allowing the application to store both the offset and the results of the consumption in the same system in a way that both the results and offsets are     stored atomically. This will give us "exactly-once" semantics
* Kafka consumer stores the last offset read in a special Kafka topic
* Tuning for throughput
  * `fetch.min.bytes` - The broker won't send messages to the consumer, until this much volume of data is available or `fetch.max.wait.ms` duration is over.      It reduces the consumer load for processing a message. The tradeoff is latency
  * `fetch.max.wait.ms`
* Tuning for latency
  * `fetch.min.bytes = 1` (default value) - The broker will send message as soon as there is at least 1 byte data available
* Tuning for durability 
  * `enable.auto.commit = false`

## Kafka Streams

* StreamsConfig.APPLICATION_ID_CONFIG (application.id) -
  * Mandatory
  * Consumer group id
  * Internal topic name prefix
  * Client id prefix
* ConsumerConfig.AUTO_OFFSET_RESET_CONFIG (auto.offset.reset) - 
  * It's applicable when the consumer group doesn't have any offset associated in Kafka
  * The possible values are - earliest (read the messages from the begining), latest (read the new messages)
* Some stream processing applications don’t require state – they are stateless – which means the processing of a message is independent from the processing of    other messages. Examples are when you only need to transform one message at a time, or filter out messages based on some condition
* In practice, however, most applications require state – they are stateful – in order to work correctly, and this state must be managed in a fault-tolerant      manner. Your application is stateful whenever, for example, it needs to join, aggregate, or window its input data
* Kafka Streams uses RocksDB (other DBs are also pluggable) to store local states
* States in application as well as remote states in other instances of the application can be queried from within the application. However, the data will be      read-only
* For fault-tolerance of state-store, an internal compacted changelog topic is used
* The state store sends changes to the changelog topic in a batch, either when a default batch size has been reached or when the commit interval is reached
* If a task crashes and get restarted on different machine, this internal changelog topic is used to recover the state store. Currently, the default              replication factor of internal topics is 1
* There are two main differences between non-windowed and windowed aggregation with regard to key-design
  * For window aggregation the key is <K,W>, i.e., for each window a new key is used
  * Instead of using a single instance, Streams uses multiple instances (called “segments”) for different time periods
* After the window retention time has passed old segments can be dropped. Thus, RocksDB memory requirement does not grow infinitely
* In contrast to KTable a GlobalKTable's state holds a full copy of the underlying topic, thus all keys can be queried locally
* Stateless transformations – Branch, Filter, Inverse Filter, FlatMap, ForEach, GroupByKey, GroupBy, Map, Peek, Print, SelectKey, Table To Stream
* Join operands - KStream-to-Kstream, KTable-to-KTable, Kstream-to-Ktable, KStream-to-GlobalKTable
* KStream-KStream join is always windowed join
* Outer join is supported only for KStream-to-KStream & KTable-to-KTable
* No join is supported for KTable-to-GlobalKTable
* Windowed joins are not supported for KStream-to-KTable, KTable-to-KTable, Kstream-to-GlobalKTable
* Input data must be co-partitioned when joining. This ensures that input records with the same key, from both sides of the join, are delivered to the same stream task during processing. It is the responsibility of the user to ensure data co-partitioning when joining
* The requirements for data co-partitioning are:
  * The input topics of the join (left side and right side) must have the same number of partitions.
  * All applications that write to the input topics must have the same partitioning strategy so that records with the same key are delivered to same partition number
* GlobalKTables do not require co-partitioning
* Kafka streams cannot verify the co-partitioning requirement for partition strategy
* Kafka streams throws `TopologyBuilderException` if the number of partitions on both sides of the join are not same
* Stream joining windows
  * Tumbling time window - Fixed-size, non-overlapping, gap-less windows
  * Hopping time window - Hopping time windows are windows based on time intervals. They model fixed-sized, (possibly) overlapping windows. A hopping window is defined by two properties: the window’s size and its advance interval (aka “hop”). The advance interval specifies by how much a window moves forward relative to the previous one
  * Sliding time window - A sliding window models a fixed-size window that slides continuously over the time axis; here, two data records are said to be included in the same window if (in the case of symmetric windows) the difference of their timestamps is within the window size. Thus, sliding windows are not aligned to the epoch, but to the data record timestamps
  * Session window

## Kafka Connect

* Kafka Connect is a tool for scalably and reliably streaming data between Apache Kafka and other systems
* Connectors can be configured with transformations to make lightweight message-at-a-time modifications
* To copy data between Kafka and another system, users create a Connector for the system they want to pull data from or push data to
* Connectors come in two flavors: `SourceConnectors` import data from another system (e.g. `JDBCSourceConnector` would import a relational database into Kafka)   and `SinkConnectors` export data (e.g. `HDFSSinkConnector` would export the contents of a Kafka topic to an HDFS file)
* Connectors do not perform any data copying themselves: their configuration describes the data to be copied, and the Connector is responsible for breaking       that job into a set of Tasks that can be distributed to workers
* Tasks also come in two corresponding flavors: `SourceTask` and `SinkTask`
* With an assignment in hand, each Task must copy its subset of the data to or from Kafka
* The SourceTask implementation included a stream ID (the input filename) and offset (position in the file) with each record. The framework uses this to commit   offsets periodically so that in the case of a failure, the task can recover and minimize the number of events that are reprocessed and possibly duplicated

## Schema Registry

* Schema Registry defines a scope in which schemas can evolve, and that scope is the subject. The name of the subject depends on the configured subject name strategy, which by default is set to derive subject name from topic name
* KafkaAvroSerializer and KafkaAvroDeserializer default to using <topicName>-Key and <topicName>-value as the corresponding subject name while registering or retrieving the schema
* The default behavior can be modified using the following properties:
  * key.subject.name.strategy - Determines how to construct the subject name under which the key schema is registered with the Schema Registry
  * value.subject.name.strategy - Determines how to construct the subject name under which the value schema is registered with Schema Registry
* Integration with Schema Registry means that Kafka messages do not need to be written with the entire Avro schema. Instead, Kafka messages are written with      the schema id. The producers writing the messages and the consumers reading the messages must be using the same Schema Registry to get the same mapping         between a schema and schema id
* A producer sends the new schema for Payments to Schema Registry. Schema Registry registers this schema Payments to the subject transactions-value, and returns the schema id of 1 to the producer. The producer caches this mapping between the schema and schema id for subsequent message writes, so it only contacts Schema Registry on the first schema write
* When a consumer reads this data, it sees the Avro schema id of 1 and sends a schema request to Schema Registry. Schema Registry retrieves the schema associated to schema id 1, and returns the schema to the consumer. The consumer caches this mapping between the schema and schema id for subsequent message reads, so it only contacts Schema Registry the on first schema id read
* Best practice is to register schemas outside of the client application to control when schemas are registered with Schema Registry and how they evolve.
* Disable automatic schema registration by setting the configuration parameter auto.register.schemas=false, as shown in the example below
  ```
  props.put(AbstractKafkaAvroSerDeConfig.AUTO_REGISTER_SCHEMAS, false);

  ```

## KSQL

* The DDL statements (Imperative verbs that define metadata on the KSQL server by adding, changing, or deleting streams and tables) include:
  * CREATE STREAM
  * CREATE TABLE
  * DROP STREAM
  * DROP TABLE
  * CREATE STREAM AS SELECT (CSAS)
  * CREATE TABLE AS SELECT (CTAS)
* The DML statements (Declarative verbs that read and modify data in KSQL streams and tables. Data Manipulation Language statements modify data only and don’t    change metadata) include:
  * SELECT
  * INSERT INTO
  * CREATE STREAM AS SELECT (CSAS)
  * CREATE TABLE AS SELECT (CTAS)
* The CSAS and CTAS statements occupy both categories, because they perform both a metadata change, like adding a stream, and they manipulate data, by creating   a derivative of existing records


## Kafka CLI

**Start Zookeeper with default configuration**

```
bin\windows\zookeeper-server-start.bat config\zookeeper.properties
```

**Start Kafka broker with default configuration**

```
bin\windows\kafka-server-start.bat config\server.properties
```

**Create a new topic**

```
bin\windows\kafka-topics.bat ^
    --create ^
    --zookeeper localhost:2181 ^
    --replication-factor 1 ^
    --partitions 2 ^
    --topic word-count-input
```

**Delete a topic**

* If the flag `delete.topic.enable` is not enabled, the following command will be ignored
* The process is asynchronous and hence the topic won't be deleted immediately

```
bin\windows\kafka-topics.bat ^
    --delete ^
    --zookeeper localhost:2181 ^
    --topic word-count-input
```

**Adding partitions**

* Key to partition mapping will change if the number of partitions is changed
* It is not advisable to add partitions to topics that have keys
* Deletion of partition is not possible

```
bin\windows\kafka-topics.bat ^
    --alter ^
    --zookeeper localhost:2181 ^
    --topic word-count-input ^
    --partitions 16 ^
```

**List topics**

```
bin\windows\kafka-topics.bat ^
    --list ^
    --zookeeper localhost:2181
```

**Describe a topic**

The output includes - partition count, topic configuration overrides, linting of each partition and its replica assignments

```
bin\windows\kafka-topics.bat ^
    --describe ^
    --zookeeper localhost:2181 ^
    --topics-with-overrides ^       REM Display topics having configurtion overrides
    --under-replicated-partitions ^ REM Display partitions with one or more out-of-sync replicas
    --unavailable-partitions        REM Display partitions without a leader
```

**List consumer groups**

```
bin\windows\kafka-consumer-groups.bat ^
    --bootstrap-server localhost:9092 ^
    --list
```

**Describe consumer group**

Among other things following fields are displayed

* CURRENT-OFFSET - The next offset to be consumed by the consumer group
* LOG-END-OFFSET - The current high-water mark offset from the broker for the topic partition. The offset of the next message to be produced to this partition
* LAG - The difference between the consumer Current-Offset and the broker Log-End-Offset for the topic partition

```
bin\windows\kafka-consumer-groups.bat ^
    --bootstrap-server localhost:9092 ^
    --describe ^
    --group testgroup ^
    --members ^ REM Lists active members
    --verbose   REM Gives partition assignments

```

**Delete consumer group**

```
bin\windows\kafka-consumer-groups.bat ^
    --bootstrap-server localhost:9092 ^
    --delete ^
    --group my-group ^
    --group my-other-group
```

**Reset Offset**

```
bin\windows\kafka-consumer-groups.bat ^
    --bootstrap-server localhost:9092 ^
    --reset-offsets ^
    --group consumergroup1 ^
    --topic topic1 ^
    --to-latest
```

**Adding or decommissioning brokers**

* The following command only generates the plan. However, it is not aware of partitions size, and neither can provide a plan to reduce the number of partitions   to migrate from brokers to brokers. Therefore, edit the plan on your own
* Once the plan is ready, execute it with the option `--execute`
* Finally verify the status using `--verify`
* Throttling is important to ensure producers and consumers in the cluster are not impacted
* The throttle can be changed even when the rebalance is ongoing by rerunning the same command with the new throttle value
* `--verify` option removes the throttle

```
bin\windows\kafka-reassign-partitions.sh 
    --zookeeper localhost:2181 
    --topics-to-move-json-file topics-to-move.json 
    --broker-list "5,6" 
    --generate
    --—throttle 50000000  REM 50MB/sec
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

## Miscellaneous

### Production Deployment

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
* `vm.max_map_count` = 65535 - Maximum number of memory map areas a process may have (aka `vm.max_map_count`). By default, on a number of Linux systems, the      value of `vm.max_map_count` is somewhere around 65535. Each log segment, allocated per partition, requires a pair of index/timeindex files, and each of these   files consumes 1 map area. In other words, each log segment uses 2 map areas. Thus, each partition requires minimum 2 map areas, as long as it hosts a single   log segment. That is to say, creating 50000 partitions on a broker will result allocation of 100000 map areas and likely cause broker crash with                OutOfMemoryError (Map failed) on a system with default `vm.max_map_count`
* File descriptor limits: Kafka uses file descriptors for log segments and open connections. If a broker hosts many partitions, consider that the broker needs    at least (number_of_partitions)*(partition_size/segment_size) to track all log segments in addition to the number of connections the broker makes. We           recommend at least 100,000 allowed file descriptors for the broker processes as a starting point
* Kafka uses heap space very carefully and does not require setting heap sizes more than 6 GB
* You can do a back-of-the-envelope estimate of memory needs by assuming you want to be able to buffer for 30 seconds and compute your memory need as             `write_throughput * 30`
* A machine with 64 GB of RAM is a decent choice, but 32 GB machines are not uncommon. Less than 32 GB tends to be counterproductive
* Do not share the same drives used for Kafka data with application logs or other OS filesystem activity to ensure good latency
* You should use RAID 10 if the additional cost is acceptable. Otherwise, configure your Kafka server with multiple log directories, each directory mounted on    a separate drive
* You should avoid network-attached storage (NAS). NAS is often slower, displays larger latencies with a wider deviation in average latency, and is a single      point of failure
* Modern data-center networking (1 GbE, 10 GbE) is sufficient for the vast majority of clusters.
* You should avoid clusters that span multiple data centers, even if the data centers are colocated in close proximity; and avoid clusters that span large        geographic distances
* `num.partitions` - The default number of log partitions for auto-created topics. You should increase this since it is better to over-partition a topic.         Over-partitioning a topic leads to better data balancing and aids consumer parallelism. For keyed data, you should avoid changing the number of partitions in   a topic
* Key Service Goals
  * Throughput
  * Latency
  * Durability
  * Availability
* Partitions are the unit of parallelism. On both the producer and the broker side, writes to different partitions can be done fully in parallel. Therefore, in   general, more partitions lead to higher throughput. However, following tradeoffs need to be considered
  * More partitions need more open file handles (3 per segment - the segment itself, offset index, time index)
  * More partitions need more memory map areas (2 per segment - the offset index, time index)
  * ~~More partitions may increase unavailability - In the event of broker failure, the partitions for which the broker is a leader may remain unavailable for    sometime as the partition leader need to be elected by modifying the Zookeeper metadata sequentially for each partition. If the failed broker happens to      be the controller, it would mean a further downtime for the partitions as a new broker elected as the new controller needs to read the metadata from          Zookeeper for each partition in the cluster. A total downtime of a few seconds for the impacted partitions are expected~~ Post release 1.1.0 with the use     of Zookeeper async APIs and batching request to other brokers during failover, things have improved here
  * More partitions may increase latency as a limited number of threads in a given broker will do replication in the broker for all the partitions in the         cluster. Unless the in-sync replicas are all updated (committed), the message will not be available to the consumers
  * More partitions means more memory requirements for producer as each producer thread will have some buffer for each partiton
* As a rule of thumb, we recommend each broker to have up to 4,000 partitions and each cluster to have up to 200,000 partitions

### Deploying on AWS

* EBS st1 for `log.dirs`
* d2.xlarge if you’re using instance storage, or r4.xlarge if you’re using EBS
* Kafka was designed to run within a single data center. As such, we discourage distributing brokers in a single cluster across multiple regions. However, we     recommend “stretching” brokers in a single cluster across availability zones within the same region
* Kafka 0.10 supports rack awareness, which makes spreading replicas across availability zones much easier to configure

### ZooKeeper Basics

* A Zookeeper cluster is called an ensemble
* Due to the algorithm used, it is recommended that ensembles contain an odd number of servers (e.g., 3, 5, etc.)
* A three-node ensemble can run with one node missing
* A five-node ensemble can run with two nodes missing
* It's a good idea to run Zookeeper in a five-node ensemble so that the ensemble can run even when one node goes down while another node is taken down due to configuration change etc.
* ZooKeeper parameters
  * `initLimit` - the amount of time to allow the followers to connect with a leader
  * `syncLimit` - Limits how out-of-sync followers can be with the leader
  * `tickTime` - Both values are a number of tickTime units
* In the following example, the `initLimit` is 20 * 2000 ms or 40 seconds
  ```
  tickTime=2000
  initLimit=20
  syncLimit=5
  ```

### Avro Basics

* BACKWARD: consumer using schema X can process data produced with schema X or X-1
* BACKWARD_TRANSITIVE: consumer using schema X can process data produced with schema X, X-1, or X-2
* FORWARD: data produced using schema X can be ready by consumers with schema X or X-1
* FORWARD_TRANSITIVE: data produced using schema X can be ready by consumers with schema X, X-1, or X-2
* FULL: backward and forward compatible between schemas X and X-1
* FULL_TRANSITIVE: backward and forward compatible between schemas X, X-1, and X-2
* Allowed operations for FULL & FULL_TRANSITIVE
  * Add optional fields
  * Delete optional fields
* Allowed operations for BACKWARD & BACKWARD_TRANSITIVE
  * Add optional fields
  * Delete fields
* Allowed operations for FORWARD & FORWARD_TRANSITIVE
  * Add fields
  * Delete optional fields
* Order of upgrading clients
  * BACKWARD or BACKWARD_TRANSITIVE: there is no assurance that consumers using older schemas can read data produced using the new schema. Therefore, upgrade all consumers before you start producing new events.
  * FORWARD or FORWARD_TRANSITIVE: there is no assurance that consumers using the new schema can read data produced using older schemas. Therefore, first upgrade all producers to using the new schema and make sure the data already produced using the older schemas are not available to consumers, then upgrade the consumers.
  * FULL or FULL_TRANSITIVE: there are assurances that consumers using older schemas can read data produced using the new schema and that consumers using the new schema can read data produced using older schemas. Therefore, you can upgrade the producers and consumers independently.
* Primitive data types –
  * null, boolean, int, long, float, double, bytes, string
* Complex data types –
  * record, enum, array, map, union, fixed
* Record attributes –
  * name, namespace, doc, aliases, type, fields
* Enum attributes
  * name, namespace, aliases, doc, symbols


## References

### Books

* Narkhede, Neha. Kafka: The Definitive Guide . O'Reilly Media. Kindle Edition
* Kleppmann, Martin. Making Sense of Stream Processing: The Philosophy Behind Apache Kafka and Scalable Stream Data Platforms . O'Reilly

### White Paper

* Byzek, Yeva. Optimizing Your Apache Kafka Deployment: Levers for Throughput, Latency, Durability, and Availability

### Useful Blogs on Operational Stuff
* https://engineering.linkedin.com/kafka/benchmarking-apache-kafka-2-million-writes-second-three-cheap-machines
* https://linux-kernel-labs.github.io/master/labs/memory_mapping.html
* https://www.confluent.io/blog/how-choose-number-topics-partitions-kafka-cluster/
* https://www.confluent.io/blog/apache-kafka-supports-200k-partitions-per-cluster/
* https://www.confluent.io/blog/design-and-deployment-considerations-for-deploying-apache-kafka-on-aws/
* https://labs.tabmo.io/rebalancing-kafkas-partitions-803918d8d244

### Udemy Courses

* Udemy courses by https://www.udemy.com/user/stephane-maarek/

### Others

* https://jira.apache.org/jira/projects/KAFKA/issues
* https://cwiki.apache.org/confluence/collector/pages.action?key=KAFKA
* https://kafka.apache.org/
* https://www.confluent.io/blog/
