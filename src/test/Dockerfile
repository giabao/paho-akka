# Helper Dockerfile for local testing
# Build by running this command from project root folder:
# (if error `Hash Sum mismatch` then you need rerun the command)
#   docker build --rm -t sbt-mosquitto src/test
# Run docker from project root folder:
#   docker run --rm -it -v $HOME/.ivy2:/home/sandinh/.ivy2 -v $PWD:/src sbt-mosquitto
# Then test inside the container:
#   sbt
# Then run sbt command as normal, ex:
#   testOnly com.sandinh.paho.akka.ResubscribeSpec
FROM java:8

ENV SBT_VERSION 0.13.11

RUN \
  curl -Lo mosquitto.key http://repo.mosquitto.org/debian/mosquitto-repo.gpg.key && \
  apt-key add mosquitto.key && \
  rm -f mosquitto.key && \
  curl -Lo /etc/apt/sources.list.d/mosquitto.list http://repo.mosquitto.org/debian/mosquitto-jessie.list && \
  sed -i s"/exit 101/exit 0"/ /usr/sbin/policy-rc.d && \
  apt-get update -q && \
  DEBIAN_FRONTEND=noninteractive apt-get install -y sudo mosquitto && \
  service mosquitto stop && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/*

RUN \
  curl -sL https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt > /bin/sbt && \
  chmod 0755 /bin/sbt && \
  adduser --disabled-password --gecos "" sandinh && \
  echo 'sandinh ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers

USER sandinh

RUN \
  curl -sLk --create-dirs -o ~/.sbt/launchers/$SBT_VERSION/sbt-launch.jar \
    https://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/$SBT_VERSION/sbt-launch.jar

VOLUME ["$HOME/.ivy2", "/src"]

WORKDIR /src

CMD bash -l
