#!/bin/bash

# install codecov python module
pip install --user codecov

# install codacy-coverage-reporter
JPM_JAR=t.jar
$(curl -sL http://www.jpm4j.org/install/script | grep curl | sed 's/>.*//g') > $JPM_JAR
java -jar $JPM_JAR -h jpadir init
rm $JPM_JAR
jpadir/bin/jpm install com.codacy:codacy-coverage-reporter:assembly


# We don't need to install and run ZooKeeper and Kafka if we are not runnin fullAcceptanceTest
[[ $TEST_SUITE =~ fullAcceptanceTest ]] || exit

function wait_for() {
    while ! nc -z localhost $1 ; do sleep 1 ; done
}

echo Installing Kafka and ZooKeeper
eval $(grep ^ENV local-storages/Dockerfile.storages | sed 's/^ENV //g')
source local-storages/install-kafka.sh

pushd $KAFKA_IMG

echo Starting ZooKeeper
bin/zookeeper-server-start.sh config/zookeeper.properties &> /dev/null &
wait_for 2181

echo Starting Kafka
bin/kafka-server-start.sh config/server.properties &> /dev/null &
wait_for 9092

popd

echo "Creating database and user"

echo "CREATE ROLE nakadi WITH LOGIN CREATEROLE PASSWORD 'nakadi'; CREATE DATABASE local_nakadi_db OWNER nakadi;" \
    | psql -h localhost -U ${PGUSER:-postgres} -d postgres
