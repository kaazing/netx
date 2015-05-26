#netx

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kaazing/netx?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status][build-status-image]][build-status]

[build-status-image]: https://travis-ci.org/kaazing/netx.svg?branch=develop
[build-status]: https://travis-ci.org/kaazing/netx

netx contains Java-based implementations and extensions for TCP, HTTP, and WebSocket protocols:

* [BBOSH](bbosh/README.md)
* [DATA](data/README.md)
* [HTTP](http/README.md)
* [TCP](tcp/README.md)
* [WebSocket](ws/README.md)

Using the following maven dependency, developers can include netx.ws to develop and run web applications:

```xml
<dependency>
    <groupId>org.kaazing</groupId>
    <artifactId>netx</artifactId>
    <version>[0.1,1.0)</version>
</dependency>
```

## Building this Project

### Minimum requirements for building the project

* Java SE Development Kit (JDK) 7 or higher
* maven 3.0.5 or higher

### Steps for building this project

0. Clone the repo: ```git clone https://github.com/kaazing/netx.git```
0. Go to the cloned directory: ```cd netx```
0. Build the project: ```mvn clean install```

## Target JVM Version - Java SE 1.6

netx is compiled using `-target=1.6` in order that the generated classes can be used to develop both desktop and
mobile (Android) apps using the same jars.

