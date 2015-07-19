# netx.ws

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kaazing/gateway?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status][build-status-image]][build-status]

[build-status-image]: https://travis-ci.org/kaazing/netx.ws.svg?branch=develop
[build-status]: https://travis-ci.org/kaazing/netx.ws

netx.ws is a Java-based implementation of [WebSocket RFC 6455](https://tools.ietf.org/html/rfc6455) client with following
important characteristics:

* Target JVM Version - Java SE 1.6
* `java.net.Socket` and `java.net.URLConnection` style APIs
* Streaming API
  * Agnostic of Message Boundaries
    * Streaming Binary Payload
    * Streaming Text Payload
  * Aware of Message Boundaries
    * Streaming In Fragmented Messages
    * Streaming Out Fragmented Messages
* Sending and Receiving Messages
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

## Streaming API

Both `org.kaazing.netx.ws.WebSocket` and `org.kaazing.netx.ws.WsURLConnection` support APIs for streaming both binary and text
payloads. One can choose to use Streaming APIs in a way that will allow the application to be either completely agnostic of
the message boundaries or aware of the message boundaries.

### Agnostic of Message Boundaries

In order to stream in either binary or text payloads **without** caring for the message boundaries, an application can obtain
`java.io.InputStream` or `java.io.Reader` objects directly off of `org.kaazing.netx.ws.WebSocket` or
`org.kaazing.netx.ws.WsURLConnection` objects using `getInputStream()` or `getReader()` APIs. When using these APIs, the
application will not know where/when the payload for one message ends and where/when the payload for the next message begins.
This implies that if an application is streaming in binary payload, then the incoming message traffic must **not** contain any
text frames and vice-versa.

#### Streaming Binary Payload

The following code illustrates streaming in binary payload without being aware of the message boundaries:

``` java
WebSocketFactory factory = WebSocketFactory.newInstance();
WebSocket connection = factory.createWebSocket(URI.toString("http://echo.websocket.org"));
InputStream in = connection.getInputStream();

byte[] buf = new byte[125];
int offset = 0;
int bytesRead = 0;

while (bytesRead != -1) {
    // Stream binary payload till the connection is closed. If a text message is received,
    // then an IOException is thrown.
    bytesRead = in.read(buf, offset, buf.length - offset);
    if (bytesRead == -1) {
        // Connection is closed.
        break;
    }
    
    offset += bytesRead;
    
    if (offset == buf.length) {
        // Do something with buf.
        ...
        ...
        // Reset offset to keep streaming more binary data.
        offset = 0;
    }
}
```

`org.kaazing.netx.ws.WsURLConnection` also supports `getInputStream()` API. In order to stream out binary payload, an application
can use `getOutputStream()` API on both `org.kaazing.netx.ws.WebSocket` and `org.kaazing.netx.ws.WsURLConnection` to obtain a
reference to the `java.io.OutputStream` object.

#### Streaming Text Payload

The following code illustrates streaming in text payload without being aware of the message boundaries:

``` java
URLConnectionHelper helper = URLConnectionHelper.newInstance();
WsURLConnection connection = (WsURLConnection) helper.openConnection(URI.create("ws://echo.websocket.org"));
Reader reader = connection.getReader();

char[] cbuf = new char[125];
int offset = 0;
int charsRead = 0;

while (charsRead != -1) {
    // Stream text payload till the connection is closed. If a binary message is received,
    // then an IOException is thrown.
    charsRead = reader.read(cbuf, offset, cbuf.length - offset);
    if (charsRead == -1) {
        // Connection is closed.
        break;
    }
    
    offset += charsRead;
    
    if (offset == cbuf.length) {
        // Do something with cbuf.
        ....
        ....
        // Reset offset to keep streaming more text data.
        offset = 0;
    }
}
```

`org.kaazing.netx.ws.WebSocket` also supports `getReader()` API. In order to stream out text payload, an application
can use `getWriter()` API on both `org.kaazing.netx.ws.WebSocket` and `org.kaazing.netx.ws.WsURLConnection` to obtain a
reference to the `java.io.Writer` object.

Also, netx.ws handles multi-byte UTF-8 characters that may be split across WebSocket frames properly.

### Aware of Message Boundaries

If an application needs to be aware of the beginning and the end of the message, then `org.kaazing.netx.ws.MessageReader` and
`org.kaazing.netx.ws.MessageWriter` should be used. An instance of these classes can be obtained from both
`org.kaazing.netx.ws.WebSocket` and `org.kaazing.netx.ws.WsURLConnection` objects using `getMessageReader()` and
`getMessageWriter()' APIs.

`org.kaazing.netx.ws.MessageReader` and `org.kaazing.netx.ws.MessageWriter` support APIs for receiving/sending messages that
fit in single WebSocket frame as well as streaming fragmented messages that span across multiple WebSocket frames. Once a message
has been streamed in completely, the blocking API `org.kaazing.netx.ws.MessageReader.next()` must be invoked to determine the
type of the next message. Furthermore, the messages can have either binary or text payload. The blocking call
`org.kaazing.netx.ws.MessageReader.next()` will return only when the next message arrives or the connection is closed.

#### Streaming In Fragmented Messages

The following code illustrates streaming in fragmented binary and text messages that span across multiple WebSocket frames:

``` java
WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
connection.setMaxPayloadLength(1024);
MessageReader messageReader = connection.getMessageReader();

byte[] binary = new byte[connection.getMaxPayloadLength()];
char[] text = new char[connection.getMaxPayloadLength()];
MessageType type = null;

while ((type = messageReader.next()) != EOS) {
    int bytesRead = 0;
    int charsRead = 0;
    int offset = 0;

    switch (type) {
    case BINARY:
        assert message.streaming();  // message spans across multiple WebSocket frames

        InputStream in = messageReader.getInputStream();
        while ((bytesRead != -1) && (offset < binary.length)) {
            bytesRead = in.read(binary, offset, binary.length - offset);
            if (bytesRead == -1) {
                // Binary message has been read completely. Do something with it.
                ....
                ....
                // Read the next message.
                break;
            }
            else {
                offset += bytesRead;
            }
        }
        break;

    case TEXT:
        assert message.streaming();  // message spans across multiple WebSocket frames

        Reader reader = messageReader.getReader();

        while ((charsRead != -1) && (offset < text.length)) {
            charsRead = reader.read(text, offset, text.length - offset);
            if (charsRead == -1) {
                // Text message has been read completely. Do something with it.
                ....
                ....
                // Read the next message.
                break;
            }
            else {
                offset += charsRead;
            }
        }
        break;
    }
}
```

#### Streaming Out Fragmented Messages

The following code illustrates streaming out fragmented binary message that spans across multiple WebSocket Frames:

``` java
WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
connection.setMaxPayloadLength(125);
MessageWriter messageWriter = connection.getMessageWriter();

byte[] binaryFrame = new byte[25];
int fragmentCount = 5;
OutputStream binaryOutputStream = messageWriter.getOutputStream();

// Stream out a binary message that spans across five WebSocket frames. Each frame contains
// 25 bytes of binary payload.
while (fragmentCount > 0) {
    fragmentCount--;
    random.nextBytes(binaryFrame);

    binaryOutputStream.write(binaryFrame);

    if (fragmentCount > 0) {
         // Send the CONTINUATION frame.
         binaryOutputStream.flush();
    }
    else {
         // Close the stream and send the final frame of the message with FIN bit set.
         binaryOutputStream.close();
    }
}
```

An application can use `java.io.OutputStream.flush()` to send out a CONTINUATION WebSocket frame for the message. And, the
final WebSocket frame of the message can be sent by invoking `java.io.OutputStream.close()` method.

Similarly, a fragmented text message that spans across multiple WebSocket frames can be streamed using 
`org.kaazing.netx.ws.MessageWriter.getWriter()`.

## Sending and Receiving Messages

Applications can send and receive both binary and text messages using either `org.kaazing.netx.ws.WebSocket` or
`org.kaazing.netx.ws.WsURLConnection`.

### Sending Binary Message

The following code illustrates sending a binary message that fits in a single WebSocket frame using
`org.kaazing.netx.ws.MessageWriter.writeFully()` API:

``` java
WebSocketFactory factory = WebSocketFactory.newInstance();
WebSocket connection = factory.createWebSocket(URI.create("http://echo.websocket.org"));
MessageWriter messageWriter = connection.getMessageWriter();
byte[] bytes = new byte[125];
Random random = new Random();

random.nextBytes(bytes);
messageWriter.writeFully(bytes);
```

### Sending Text Message

The following code illustrates sending a text message that fits in a single WebSocket frame using
`org.kaazing.netx.ws.MessageWriter.writeFully()` API:

``` java
URLConnectionHelper helper = URLConnectionHelper.newInstance();
WsURLConnection connection = (WsURLConnection) helper.openConnection(URI.create("ws://echo.websocket.org"));
MessageWriter messageWriter = connection.getMessageWriter();
char[] cbuf = new char[125];

// populate cbuf

messageWriter.writeFully(cbuf);
```

### Receiving Message

The following code illustrates receiving a binary or a text message that fits in a single WebSocket frame using
`org.kaazing.netx.ws.MessageReader.readFully() API`:

``` java
WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
connection.setMaxPayloadLength(1024);
MessageReader messageReader = connection.getMessageReader();

byte[] binaryFull = new byte[connection.getMaxPayloadLength()];
char[] textFull = new char[connection.getMaxPayloadLength()];
MessageType type = null;

while ((type = messageReader.next()) != EOS) {
    switch (type) {
    case BINARY:
        assert !message.streaming();  // message fits in a single WebSocket frame
        messageReader.readFully(binaryFull);
        break;

    case TEXT:
        assert !message.streaming();  // message fits in a single WebSocket frame
        messageReader.readFully(textFull);
        break;
    }
}
```

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
receiving messages using `java.io.InputStream`, `java.io.Reader`, or `org.kaazing.netx.ws.MessageReader`.

## Garbage-free
netx.ws is garbage-free. Once the initial warm up for a message of specified length is complete, netx.ws will not do any more
allocations as long as the message length stays at or below the specified length.

## Wait-free
netx.ws synchronizes the sending and receiving threads using optimistic locking so there should be no contention or blocked
threads.
