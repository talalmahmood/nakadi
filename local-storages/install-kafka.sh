curl -sL http://ftp.halifax.rwth-aachen.de/apache/kafka/${KAFKA_VERSION}/${KAFKA_IMG}.tgz | tar xz

cat >> ${KAFKA_IMG}/config/server.properties << --

advertised.host.name=localhost
advertised.host.port=9092
auto.create.topics.enable=false
delete.topic.enable=true
--
