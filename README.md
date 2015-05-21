# netx.ws

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kaazing/gateway?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status][build-status-image]][build-status]

[build-status-image]: https://travis-ci.org/kaazing/netx.ws.svg?branch=develop
[build-status]: https://travis-ci.org/kaazing/netx.ws

netx.ws is a Java-based implementation of [WebSocket RFC 6455](https://tools.ietf.org/html/rfc6455) client with following
important characteristics:

* Target JVM Version - Java SE 1.6
* `java.net.Socket` and `java.net.URLConnection` style APIs
* API for streaming binary and text payloads
* Extension SPI for extension developers
* Authentication
* HTTP Redirect Policies
* Thread-safe
* Garbage-free
* Wait-free

Using the following maven dependency, developers can include netx.ws to develop and run web applications:

```xml
<dependency>
    <groupId>org.kaazing</groupId>
    <artifactId>netx.ws</artifactId>
    <version>[0.1,1.0)</version>
</dependency>
```

## Building this Project

### Minimum requirements for building the project

* Java SE Development Kit (JDK) 7 or higher
* maven 3.0.5 or higher

### Steps for building this project

0. Clone the repo: ```git clone https://github.com/kaazing/netx.ws.git```
0. Go to the cloned directory: ```cd netx.ws```
0. Build the project: ```mvn clean install```

## Target JVM Version - Java SE 1.6

netx.ws is compiled using `-target=1.6` in order that the generated classes can be used to develop both desktop and
mobile (Android) apps using the same jars.

## java.net.Socket and java.net.URLConnection Style APIs

netx.ws offers two options for creating and using WebSocket connections that enable developers to leverage their
`java.net.Socket` and `java.net.URLConnection` experience:

 * `org.kaazing.netx.ws.WebSocket` - for developers familiar with the `java.net.Socket` class and it's APIs.
 * `org.kaazing.netx.ws.WsURLConnection` - for developers familiar with the `java.net.URLConnection` and it's APIs.

### org.kaazing.netx.ws.WebSocket

netx.ws offers `org.kaazing.netx.ws.WebSocketFactory` to create instances of `org.kaazing.netx.ws.WebSocket` as shown below:

 ``` java
 WebSocketFactory factory = WebSocketFactory.newInstance();
 WebSocket connection = factory.createWebSocket(URI.toString("http://echo.websocket.org"));
 ```
### org.kaazing.netx.ws.WsURLConnection

The following code shows how to create an instance of `org.kaazing.netx.ws.WsURLConnection` using
`org.kaazing.netx.URLConnectionHelper`:

``` java
URLConnectionHelper helper = URLConnectionHelper.newInstance();
WsURLConnection connection = (WsURLConnection) helper.openConnection(URI.create("ws://echo.websocket.org"));
```

## Stream Binary and Text Payloads

Both `org.kaazing.netx.ws.WebSocket` and `org.kaazing.netx.ws.WsURLConnection` support APIs for streaming both binary and text
payloads. Note that the WebSocket frame boundaries are ignored during streaming. This implies that if an application is
streaming binary payload, then the incoming message traffic must **not** contain any text frames and vice-versa.

### Streaming Binary Payload Using java.io.InputStream

The following code illustrates streaming binary payloads using `org.kaazing.netx.ws.WebSocket` and `java.io.InputStream`:

``` java
WebSocketFactory factory = WebSocketFactory.newInstance();
WebSocket connection = factory.createWebSocket(URI.toString("http://echo.websocket.org"));
InputStream in = connection.getInputStream();

byte[] buf = new byte[125];
int offset = 0;
int length = buf.length;
int bytesRead = 0;

while ((bytesRead != -1) && (length > 0)) {
    bytesRead = in.read(buf, offset, length);
    if (bytesRead != -1) {
       offset += bytesRead;
       length -= bytesRead;
    }
}
```

`org.kaazing.netx.ws.WsURLConnection` also supports `getInputStream()` API. So, an app can stream binary payload using both
`org.kaazing.netx.ws.WebSocket` and `org.kaazing.netx.ws.WsURLConnection`.

### Streaming Text Payload Using java.io.Reader

The following code illustrates streaming text payloads using `org.kaazing.netx.ws.WsURLConnection` and `java.io.Reader`:

``` java
URLConnectionHelper helper = URLConnectionHelper.newInstance();
WsURLConnection connection = (WsURLConnection) helper.openConnection(URI.create("ws://echo.websocket.org"));
Reader reader = connection.getReader();

char[] cbuf = new char[125];
int offset = 0;
int length = cbuf.length;
int charsRead = 0;

while ((charsRead != -1) && (length > 0)) {
    charsRead = reader.read(cbuf, offset, length);
    if (charsRead != -1) {
       offset += charsRead;
       length -= charsRead;
    }
}
```

`org.kaazing.netx.ws.WebSocket` also supports `getReader()` API. So, an app can stream text payload using both
`org.kaazing.netx.ws.WebSocket` and `org.kaazing.netx.ws.WsURLConnection`. Also, netx.ws handles multi-byte UTF-8 characters
that may be split across WebSocket frames properly.

## Send Messages

Applications can send both binary and text messages using either `org.kaazing.netx.ws.WebSocket` or
`org.kaazing.netx.ws.WsURLConnection`.

### Send Binary Message Using java.io.OutputStream

The following code illustrates sending a binary message using `org.kaazing.netx.ws.WebSocket` and `java.io.OutputStream`:

``` java
WebSocketFactory factory = WebSocketFactory.newInstance();
WebSocket connection = factory.createWebSocket(URI.create("http://echo.websocket.org"));
OutputStream out = connection.getOutputStream();
byte[] bytes = new byte[125];
Random random = new Random();

random.nextBytes(bytes);
out.write(bytes);
```

`org.kaazing.netx.ws.WsURLConnection` also supports `getOutputStream()` API. So, an app can send a binary message using both
`org.kaazing.netx.ws.WebSocket` and `org.kaazing.netx.ws.WsURLConnection`.

### Send Text Message Using java.io.Writer

``` java
URLConnectionHelper helper = URLConnectionHelper.newInstance();
WsURLConnection connection = (WsURLConnection) helper.openConnection(URI.create("ws://echo.websocket.org"));
Writer writer = connection.getWriter();
char[] cbuf = new char[125];

// populate cbuf

writer.write(cbuf);
```

`org.kaazing.netx.ws.WebSocket` also supports `getWriter()` API. So, an app can send a text message using both
`org.kaazing.netx.ws.WebSocket` and `org.kaazing.netx.ws.WsURLConnection`.

## Extensions SPI

netx.ws offers a comprehensive SPI for extension-developers to build WebSocket extensions. Once an extension is successfully
negotiated between the client and the server, it will be inserted in the pipeline and may access and transform every
incoming and outgoing frame using the SPI. Currently, the SPI is available under `org.kaazing.netx.ws.internal.ext` package.
Once non-trivial extensions are developed using the SPI and we are certain that it is complete and sufficient, the SPI will be
hoisted out.

## Authentication

If netx.ws based client applications are connecting to a KAAZING Gateway, then the application must deal with the
authentication challenges in two different ways.

### Authentication Using java.net.Authenticator

If the HTTP authentication scheme is `Basic`, `Digest`, or `Negotiate`, then the application should be set up with a
`java.net.Authenticator` as shown below:

``` java
Authenticator authenticator = new Authenticator() {
    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication("joe", "welcome".toCharArray());
    }
};
Authenticator.setDefault(authenticator);
```

In this case, the JDK directly invokes the `java.net.Authenticator` to obtain the credentials and then sets `Authorization`
request header.

### Authentication Using org.kaazing.netx.http.auth.ChallengeHandler

If the HTTP authentication scheme is `Application Basic`, `Application Negotiate`, `Application Token`, then application
should be set up with an appropriate implementation of `org.kaazing.netx.http.auth.ChallengeHandler`. For example, if the
authentication scheme is `Application Basic`, then `ApplicationBasicChallengeHandler` should be setup as shown below:

``` java
URLConnectionHelper helper = URLConnectionHelper.newInstance();
WsURLConnection connection = (WsURLConnection) helper.openConnection(URI.create("ws://echo.websocket.org"));

ApplicationBasicChallengeHandler challengeHandler = ApplicationBasicChallengeHandler.create();
LoginHandler loginHandler = new LoginHandler() {
    @Override
    public PasswordAuthentication getCredentials() {
        return new PasswordAuthentication("joe", "welcome".toCharArray());
    }
};
challengeHandler.setLoginHandler(loginHandler);
connection.setChallengeHandler(challengeHandler);
```

Note that `org.kaazing.netx.ws.WebSocket` also supports the `setChallengeHandler()` API.

## HTTP Redirect Policy

netx.ws supports a rich set of HTTP redirect policies to enable an application to handle server redirects. If the app
should be redirected only if the domain of the redirect request matches the domain of the original request, then the appropriate
redirect policy can be specified shown below:

``` java
URLConnectionHelper helper = URLConnectionHelper.newInstance();
WsURLConnection connection = (WsURLConnection) helper.openConnection(URI.create("ws://echo.websocket.org"));
connection.setRedirectPolicy(HttpRedirectPolicy.DOMAIN);
```
Other redirect policies include `HttpRedirectPolicy.NEVER`, `HttpRedirectPolicy.ALWAYS`, `HttpRedirectPolicy.ORIGIN`. Note that
`org.kaazing.netx.ws.WebSocket` also supports the `setRedirectPolicy()` API.

## Thread-safe

netx.ws is thread-safe. An application can have multiple threads sending WebSocket messages using `java.io.OutputStream`,
`java.io.Writer`, or `org.kaazing.netx.ws.MessageWriter`. Similarly, there can be multiple threads
receiving messages.

## Garbage-free
netx.ws is garbage-free. Once the initial warm up for a message of specified length is complete, netx.ws will not do any more
allocations as long as the message length stays at or below the specified length.

## Wait-free
netx.ws synchronizes the sending and receiving threads using optimistic locking so there should be no contention or blocked
threads.
