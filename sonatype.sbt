publishMavenStyle := true

publishTo := sonatypePublishToBundle.value

pomExtra in Global := <url>https://github.com/giabao/paho-akka</url>
  <licenses>
    <license>
      <name>Apache 2</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>https://github.com/giabao/paho-akka</url>
    <connection>scm:git:git@github.com:giabao/paho-akka.git</connection>
  </scm>
  <developers>
    <developer>
      <id>giabao</id>
      <name>Gia Bảo</name>
      <email>giabao@sandinh.net</email>
      <organization>Sân Đình</organization>
      <organizationUrl>http://sandinh.com</organizationUrl>
    </developer>
  </developers>
